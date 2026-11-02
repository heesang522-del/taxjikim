"""PDF 내장 텍스트와 OCR 단어를 병합하고 행과 셀을 복원한다."""

from __future__ import annotations

import math
import re
import statistics

from dataclasses import dataclass
from typing import Any


@dataclass(frozen=True)
class LayoutConfig:
    """문서에 따라 조정할 수 있는 처리 기준이다."""

    coverage_threshold: float = 0.8
    same_text_overlap: float = 0.35

    row_tolerance_factor: float = 0.4
    min_row_tolerance: float = 2.0
    min_vertical_overlap: float = 0.4

    cell_gap_factor: float = 2.0
    min_cell_gap: float = 7.0

    def __post_init__(self) -> None:
        rules = {
            "coverage_threshold": (0, 1, True),
            "same_text_overlap": (0, 1, True),
            "row_tolerance_factor": (0, 1, True),
            "min_vertical_overlap": (0, 1, True),
            "cell_gap_factor": (0, None, True),
            "min_row_tolerance": (0, None, False),
            "min_cell_gap": (0, None, False),
        }

        for name, (minimum, maximum, exclusive) in rules.items():
            value = getattr(self, name)

            invalid = (
                isinstance(value, bool)
                or not isinstance(value, (int, float))
                or not math.isfinite(value)
            )

            if not invalid:
                invalid = (
                    value <= minimum
                    if exclusive
                    else value < minimum
                )

            if not invalid and maximum is not None:
                invalid = value > maximum

            if invalid:
                raise ValueError(
                    f"{name}: 설정값이 허용 범위를 벗어났습니다."
                )


DEFAULT_CONFIG = LayoutConfig()


def _normalize(value: str) -> str:
    """숫자의 부호·소수점·백분율을 보존한다."""

    return re.sub(r"\s+", "", value).casefold()


def prepare_word(
    word: dict[str, Any],
    source: str = "UNKNOWN",
) -> dict[str, Any] | None:
    """좌표와 단어 값을 검증하고 좌표 이름을 통일한다."""

    if not isinstance(word, dict):
        return None

    value = str(
        word.get("text")
        if word.get("text") is not None
        else ""
    ).strip()

    if (
        not value
        or "\ufffd" in value
        or any(
            ord(character) < 32
            and not character.isspace()
            for character in value
        )
    ):
        return None

    try:
        left = float(
            word["left"]
            if "left" in word
            else word["x0"]
        )
        top = float(word["top"])
        right = float(
            word["right"]
            if "right" in word
            else word["x1"]
        )
        bottom = float(word["bottom"])

    except (
        KeyError,
        TypeError,
        ValueError,
        OverflowError,
    ):
        return None

    if (
        not all(
            math.isfinite(number)
            for number in (left, top, right, bottom)
        )
        or right <= left
        or bottom <= top
    ):
        return None

    confidence = None

    try:
        if word.get("confidence") is not None:
            candidate = float(word["confidence"])

            if math.isfinite(candidate):
                confidence = candidate

    except (TypeError, ValueError, OverflowError):
        pass

    result = {
        "text": value,
        "left": left,
        "top": top,
        "right": right,
        "bottom": bottom,
        "source": str(word.get("source") or source),
        "confidence": confidence,
    }

    # 행 복원 단계에서도 병합 검토 사유를 유지한다.
    if word.get("reviewReasons"):
        result["reviewReasons"] = list(
            word["reviewReasons"]
        )

    return result


def _prepare_words(
    words: list[dict[str, Any]],
    source: str,
) -> list[dict[str, Any]]:
    return [
        prepared
        for word in words
        if (
            prepared := prepare_word(word, source)
        ) is not None
    ]


def _bounds(
    words: list[dict[str, Any]],
) -> list[float]:
    return [
        min(word["left"] for word in words),
        min(word["top"] for word in words),
        max(word["right"] for word in words),
        max(word["bottom"] for word in words),
    ]


def _area(
    rectangle: list[float] | tuple[float, ...],
) -> float:
    left, top, right, bottom = rectangle
    return (right - left) * (bottom - top)


def _intersection(
    first: dict[str, Any],
    second: dict[str, Any],
) -> tuple[float, float, float, float] | None:
    rectangle = (
        max(first["left"], second["left"]),
        max(first["top"], second["top"]),
        min(first["right"], second["right"]),
        min(first["bottom"], second["bottom"]),
    )

    left, top, right, bottom = rectangle

    if right <= left or bottom <= top:
        return None

    return rectangle


def _union_area(
    rectangles: list[tuple[float, float, float, float]],
) -> float:
    """겹치는 영역의 면적을 중복 합산하지 않는다."""

    if not rectangles:
        return 0.0

    boundaries = sorted(
        {
            coordinate
            for left, _, right, _ in rectangles
            for coordinate in (left, right)
        }
    )

    total = 0.0

    for left, right in zip(boundaries, boundaries[1:]):
        intervals = sorted(
            (top, bottom)
            for x0, top, x1, bottom in rectangles
            if x0 < right and x1 > left
        )

        if not intervals:
            continue

        start, end = intervals[0]
        height = 0.0

        for top, bottom in intervals[1:]:
            if top <= end:
                end = max(end, bottom)
            else:
                height += end - start
                start, end = top, bottom

        total += (
            (right - left)
            * (height + end - start)
        )

    return total


def _mark_review(
    words: list[dict[str, Any]],
    reason: str,
) -> None:
    for word in words:
        reasons = list(word.get("reviewReasons") or [])

        if reason not in reasons:
            reasons.append(reason)

        word["reviewReasons"] = reasons


def merge_words(
    native: list[dict[str, Any]],
    ocr: list[dict[str, Any]],
    *,
    config: LayoutConfig = DEFAULT_CONFIG,
) -> list[dict[str, Any]]:
    """내장 텍스트를 우선하고 OCR로 누락 영역을 보완한다."""

    result: list[dict[str, Any]] = []

    native_words = _prepare_words(native, "PDF_TEXT")

    ocr_words = sorted(
        _prepare_words(ocr, "OCR"),
        key=lambda word: (
            word["confidence"]
            if word["confidence"] is not None
            else -1.0
        ),
        reverse=True,
    )

    for is_ocr, words in (
        (False, native_words),
        (True, ocr_words),
    ):
        for word in words:
            overlaps = []

            for existing in result:
                rectangle = _intersection(word, existing)

                if rectangle is not None:
                    overlaps.append((existing, rectangle))

            duplicate = any(
                _normalize(word["text"])
                == _normalize(existing["text"])
                and _area(rectangle)
                / min(
                    _area(_bounds([word])),
                    _area(_bounds([existing])),
                )
                >= config.same_text_overlap
                for existing, rectangle in overlaps
            )

            if duplicate:
                continue

            if not is_ocr or not overlaps:
                result.append(word)
                continue

            existing_words = [
                existing
                for existing, _ in overlaps
            ]

            coverage = (
                _union_area(
                    [
                        rectangle
                        for _, rectangle in overlaps
                    ]
                )
                / _area(_bounds([word]))
            )

            if coverage >= config.coverage_threshold:
                combined_text = "".join(
                    existing["text"]
                    for existing in sorted(
                        existing_words,
                        key=lambda item: item["left"],
                    )
                )

                if (
                    _normalize(combined_text)
                    != _normalize(word["text"])
                ):
                    _mark_review(
                        existing_words,
                        "같은 위치의 기존 텍스트와 "
                        "OCR 결과가 일치하지 않습니다.",
                    )

                continue

            _mark_review(
                existing_words + [word],
                "단어 영역이 일부 겹쳐 "
                "중복 여부 확인이 필요합니다.",
            )

            result.append(word)

    return sorted(
        result,
        key=lambda word: (
            (word["top"] + word["bottom"]) / 2,
            word["left"],
        ),
    )


def _row_statistics(
    words: list[dict[str, Any]],
) -> tuple[float, float, float, float]:
    return (
        statistics.median(
            (word["top"] + word["bottom"]) / 2
            for word in words
        ),
        statistics.median(
            word["bottom"] - word["top"]
            for word in words
        ),
        statistics.median(
            word["top"]
            for word in words
        ),
        statistics.median(
            word["bottom"]
            for word in words
        ),
    )


def _group_rows(
    words: list[dict[str, Any]],
    config: LayoutConfig,
) -> list[list[dict[str, Any]]]:
    rows: list[list[dict[str, Any]]] = []

    for word in sorted(
        words,
        key=lambda item: (
            (item["top"] + item["bottom"]) / 2,
            item["left"],
        ),
    ):
        center = (word["top"] + word["bottom"]) / 2
        height = word["bottom"] - word["top"]

        selected = None
        best_distance = float("inf")

        for row in rows:
            row_center, row_height, top, bottom = (
                _row_statistics(row)
            )

            tolerance = max(
                config.min_row_tolerance,
                min(height, row_height)
                * config.row_tolerance_factor,
            )

            overlap = max(
                0.0,
                min(word["bottom"], bottom)
                - max(word["top"], top),
            ) / min(height, bottom - top)

            distance = abs(center - row_center)

            if (
                distance <= tolerance
                and overlap >= config.min_vertical_overlap
                and distance < best_distance
            ):
                selected = row
                best_distance = distance

        if selected is None:
            rows.append([word])
        else:
            selected.append(word)

    for row in rows:
        row.sort(key=lambda word: word["left"])

    return sorted(
        rows,
        key=lambda row: _row_statistics(row)[0],
    )


def _build_cells(
    words: list[dict[str, Any]],
    config: LayoutConfig,
) -> list[str]:
    """기존 글자 너비 기준으로 셀을 분리한다."""

    threshold = max(
        config.min_cell_gap,
        statistics.median(
            (word["right"] - word["left"])
            / max(1, len(_normalize(word["text"])))
            for word in words
        ) * config.cell_gap_factor,
    )

    cells = []
    current = []
    current_right = None

    for word in words:
        if (
            current
            and current_right is not None
            and word["left"] - current_right > threshold
        ):
            cells.append(" ".join(current))
            current = []
            current_right = None

        current.append(word["text"])

        current_right = (
            word["right"]
            if current_right is None
            else max(current_right, word["right"])
        )

    if current:
        cells.append(" ".join(current))

    return cells


def words_to_rows(
    words: list[dict[str, Any]],
    *,
    config: LayoutConfig = DEFAULT_CONFIG,
) -> list[dict[str, Any]]:
    """단어를 행과 셀로 복원해 거래 파서에 전달한다."""

    prepared = _prepare_words(words, "UNKNOWN")

    if not prepared:
        return []

    result = []

    for row in _group_rows(prepared, config):
        cells = _build_cells(row, config)

        reasons = sorted(
            {
                reason
                for word in row
                for reason in (
                    word.get("reviewReasons") or []
                )
            }
        )

        result.append(
            {
                "text": " | ".join(cells),
                "cells": cells,
                "bbox": _bounds(row),
                "words": row,
                "sources": sorted(
                    {word["source"] for word in row}
                ),
                "needsReview": bool(reasons),
                "reviewReasons": reasons,
            }
        )

    return result