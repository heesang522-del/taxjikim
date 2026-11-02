"""단어 좌표의 정규화, 중복 제거, 행과 셀 복원을 담당한다."""

from __future__ import annotations

import math
import re
import statistics

from dataclasses import dataclass, replace
from typing import Any


@dataclass(frozen=True)
class LayoutConfig:
    coverage_threshold: float = 0.8
    same_text_overlap: float = 0.35

    row_tolerance_factor: float = 0.4
    min_row_tolerance: float = 2.0
    min_vertical_overlap: float = 0.4

    cell_gap_factor: float = 0.7
    min_cell_gap: float = 6.0

    def __post_init__(self) -> None:
        bounded = {
            "coverage_threshold",
            "same_text_overlap",
            "row_tolerance_factor",
            "min_vertical_overlap",
        }

        positive = bounded | {
            "cell_gap_factor",
        }

        for name in (
            *bounded,
            "cell_gap_factor",
            "min_cell_gap",
            "min_row_tolerance",
        ):
            value = getattr(self, name)

            if (
                isinstance(value, bool)
                or not isinstance(value, (int, float))
                or not math.isfinite(value)
            ):
                raise ValueError(
                    f"{name}: 유한한 숫자를 입력해 주세요."
                )

            if name in positive and value <= 0:
                raise ValueError(
                    f"{name}: 0보다 커야 합니다."
                )

            if name not in positive and value < 0:
                raise ValueError(
                    f"{name}: 0 이상이어야 합니다."
                )

            if name in bounded and value > 1:
                raise ValueError(
                    f"{name}: 1 이하여야 합니다."
                )


def normalized_text(value: object) -> str:
    return re.sub(
        r"\s+",
        "",
        str(value if value is not None else ""),
    ).casefold()


def prepare_word(
    word: dict[str, Any],
    source: str = "UNKNOWN",
) -> dict[str, Any] | None:
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

    result = dict(word)
    result.pop("x0", None)
    result.pop("x1", None)

    result.update(
        text=value,
        left=left,
        top=top,
        right=right,
        bottom=bottom,
        source=str(word.get("source") or source),
        confidence=confidence,
    )

    return result


def prepare_words(
    words: list[dict[str, Any]],
    source: str = "UNKNOWN",
) -> list[dict[str, Any]]:
    return [
        prepared
        for word in words
        if (
            prepared := prepare_word(word, source)
        ) is not None
    ]


def bounds(
    words: list[dict[str, Any]],
) -> list[float]:
    return [
        min(word["left"] for word in words),
        min(word["top"] for word in words),
        max(word["right"] for word in words),
        max(word["bottom"] for word in words),
    ]


def area(word: dict[str, Any]) -> float:
    return (
        (word["right"] - word["left"])
        * (word["bottom"] - word["top"])
    )


def intersection(
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


def rectangle_area(
    rectangle: tuple[float, float, float, float],
) -> float:
    left, top, right, bottom = rectangle
    return (right - left) * (bottom - top)


def union_area(
    rectangles: list[
        tuple[float, float, float, float]
    ],
) -> float:
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

    for left, right in zip(
        boundaries,
        boundaries[1:],
    ):
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


def mark_review(
    word: dict[str, Any],
    reason: str,
    alternative: dict[str, Any] | None = None,
) -> None:
    reasons = list(
        word.get("mergeReviewReasons") or []
    )

    if reason not in reasons:
        reasons.append(reason)

    word["mergeNeedsReview"] = True
    word["mergeReviewReasons"] = reasons

    if alternative is not None:
        alternatives = list(
            word.get("ocrAlternatives") or []
        )

        entry = {
            "text": alternative["text"],
            "confidence": alternative.get("confidence"),
            "bbox": bounds([alternative]),
        }

        if entry not in alternatives:
            alternatives.append(entry)

        word["ocrAlternatives"] = alternatives


def merge_words(
    native: list[dict[str, Any]],
    ocr: list[dict[str, Any]],
    *,
    config: LayoutConfig | None = None,
    **options: Any,
) -> list[dict[str, Any]]:
    settings = replace(
        config or LayoutConfig(),
        **options,
    )

    native_words = prepare_words(native, "PDF_TEXT")

    ocr_words = sorted(
        prepare_words(ocr, "OCR"),
        key=lambda word: (
            word["confidence"]
            if word["confidence"] is not None
            else -1.0
        ),
        reverse=True,
    )

    result: list[dict[str, Any]] = []

    for word in native_words + ocr_words:
        overlaps = []

        for existing in result:
            rectangle = intersection(word, existing)

            if rectangle is not None:
                overlaps.append(
                    (existing, rectangle)
                )

        same_word = any(
            normalized_text(word["text"])
            == normalized_text(existing["text"])
            and rectangle_area(rectangle)
            / min(area(word), area(existing))
            >= settings.same_text_overlap
            for existing, rectangle in overlaps
        )

        if same_word:
            continue

        # 내장 텍스트끼리는 문자열이 다른 경우 보존한다.
        if word["source"] == "PDF_TEXT":
            result.append(word)
            continue

        coverage = (
            union_area(
                [
                    rectangle
                    for _, rectangle in overlaps
                ]
            ) / area(word)
        )

        if coverage >= settings.coverage_threshold:
            existing_text = "".join(
                existing["text"]
                for existing, _ in sorted(
                    overlaps,
                    key=lambda pair: (
                        pair[0]["top"],
                        pair[0]["left"],
                    ),
                )
            )

            if (
                normalized_text(existing_text)
                != normalized_text(word["text"])
            ):
                for existing, _ in overlaps:
                    mark_review(
                        existing,
                        "같은 위치의 내장 텍스트 또는 기존 OCR과 "
                        "추가 OCR 결과가 일치하지 않습니다.",
                        word,
                    )

            continue

        if overlaps:
            reason = (
                "단어 영역이 일부 겹쳐 중복 여부 확인이 필요합니다."
            )

            mark_review(word, reason)

            for existing, _ in overlaps:
                mark_review(existing, reason, word)

        result.append(word)

    return sorted(
        result,
        key=lambda word: (
            (word["top"] + word["bottom"]) / 2,
            word["left"],
        ),
    )


def line_statistics(
    words: list[dict[str, Any]],
) -> tuple[float, float, float, float]:
    top = statistics.median(
        word["top"] for word in words
    )
    bottom = statistics.median(
        word["bottom"] for word in words
    )
    center = statistics.median(
        (word["top"] + word["bottom"]) / 2
        for word in words
    )
    height = statistics.median(
        word["bottom"] - word["top"]
        for word in words
    )

    return center, height, top, bottom


def group_lines(
    words: list[dict[str, Any]],
    config: LayoutConfig,
) -> list[list[dict[str, Any]]]:
    lines: list[list[dict[str, Any]]] = []

    for word in sorted(
        words,
        key=lambda item: (
            (item["top"] + item["bottom"]) / 2,
            item["left"],
        ),
    ):
        center = (
            word["top"] + word["bottom"]
        ) / 2
        height = word["bottom"] - word["top"]

        selected = None
        best_distance = float("inf")

        for line in lines:
            row_center, row_height, top, bottom = (
                line_statistics(line)
            )

            tolerance = max(
                config.min_row_tolerance,
                min(height, row_height)
                * config.row_tolerance_factor,
            )

            overlap_height = max(
                0.0,
                min(word["bottom"], bottom)
                - max(word["top"], top),
            )

            smaller_height = min(
                height,
                bottom - top,
            )

            vertical_overlap = (
                overlap_height / smaller_height
                if smaller_height > 0
                else 0.0
            )

            distance = abs(center - row_center)

            if (
                distance <= tolerance
                and vertical_overlap
                >= config.min_vertical_overlap
                and distance < best_distance
            ):
                selected = line
                best_distance = distance

        if selected is None:
            lines.append([word])
        else:
            selected.append(word)

    for line in lines:
        line.sort(key=lambda word: word["left"])

    return sorted(
        lines,
        key=lambda line: line_statistics(line)[0],
    )


def build_cells(
    words: list[dict[str, Any]],
    config: LayoutConfig,
) -> list[dict[str, Any]]:
    threshold = max(
        config.min_cell_gap,
        statistics.median(
            word["bottom"] - word["top"]
            for word in words
        ) * config.cell_gap_factor,
    )

    groups: list[list[dict[str, Any]]] = []
    current: list[dict[str, Any]] = []
    right = None

    for word in words:
        if (
            current
            and right is not None
            and word["left"] - right > threshold
        ):
            groups.append(current)
            current = []
            right = None

        current.append(word)

        right = (
            word["right"]
            if right is None
            else max(right, word["right"])
        )

    if current:
        groups.append(current)

    return [
        {
            "text": " ".join(
                word["text"] for word in group
            ),
            "bbox": bounds(group),
            "words": [dict(word) for word in group],
        }
        for group in groups
    ]


def words_to_rows(
    words: list[dict[str, Any]],
    *,
    config: LayoutConfig | None = None,
    **options: Any,
) -> list[dict[str, Any]]:
    settings = replace(
        config or LayoutConfig(),
        **options,
    )

    prepared = prepare_words(words)

    if not prepared:
        return []

    result = []

    for line in group_lines(prepared, settings):
        details = build_cells(line, settings)

        reasons = sorted(
            {
                reason
                for word in line
                for reason in (
                    word.get("mergeReviewReasons") or []
                )
            }
        )

        result.append(
            {
                "text": " ".join(
                    word["text"] for word in line
                ),
                "cells": [
                    detail["text"] for detail in details
                ],
                "bbox": bounds(line),
                "words": [dict(word) for word in line],
                "cellDetails": details,
                "sources": sorted(
                    {word["source"] for word in line}
                ),
                "needsReview": bool(reasons),
                "reviewReasons": reasons,
            }
        )

    return result