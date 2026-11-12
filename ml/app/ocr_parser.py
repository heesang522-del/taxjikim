"""스캔형 PDF의 OCR 단어 좌표를 정규화하고 행과 셀로 복원한다."""

from __future__ import annotations

import math
import statistics

from dataclasses import dataclass, replace
from typing import Any


@dataclass(frozen=True)
class LayoutConfig:
    row_tolerance_factor: float = 0.4
    min_row_tolerance: float = 2.0
    min_vertical_overlap: float = 0.4

    cell_gap_factor: float = 0.7
    min_cell_gap: float = 6.0

    def __post_init__(self) -> None:
        bounded = {
            "row_tolerance_factor",
            "min_vertical_overlap",
        }
        positive = bounded | {"cell_gap_factor"}

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
                raise ValueError(f"{name}: 유한한 숫자를 입력해 주세요.")

            if name in positive and value <= 0:
                raise ValueError(f"{name}: 0보다 커야 합니다.")

            if name not in positive and value < 0:
                raise ValueError(f"{name}: 0 이상이어야 합니다.")

            if name in bounded and value > 1:
                raise ValueError(f"{name}: 1 이하여야 합니다.")


def prepare_word(
    word: dict[str, Any],
    source: str = "UNKNOWN",
) -> dict[str, Any] | None:
    if not isinstance(word, dict):
        return None

    value = str(
        word.get("text") if word.get("text") is not None else ""
    ).strip()

    if (
        not value
        or "\ufffd" in value
        or any(
            ord(character) < 32 and not character.isspace()
            for character in value
        )
    ):
        return None

    try:
        left = float(word["left"] if "left" in word else word["x0"])
        top = float(word["top"])
        right = float(word["right"] if "right" in word else word["x1"])
        bottom = float(word["bottom"])
    except (KeyError, TypeError, ValueError, OverflowError):
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
        if (prepared := prepare_word(word, source)) is not None
    ]


def bounds(words: list[dict[str, Any]]) -> list[float]:
    return [
        min(word["left"] for word in words),
        min(word["top"] for word in words),
        max(word["right"] for word in words),
        max(word["bottom"] for word in words),
    ]


def line_statistics(
    words: list[dict[str, Any]],
) -> tuple[float, float, float, float]:
    top = statistics.median(word["top"] for word in words)
    bottom = statistics.median(word["bottom"] for word in words)
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
        center = (word["top"] + word["bottom"]) / 2
        height = word["bottom"] - word["top"]

        selected = None
        best_distance = float("inf")

        for line in lines:
            row_center, row_height, top, bottom = line_statistics(line)

            tolerance = max(
                config.min_row_tolerance,
                min(height, row_height) * config.row_tolerance_factor,
            )

            overlap_height = max(
                0.0,
                min(word["bottom"], bottom) - max(word["top"], top),
            )

            smaller_height = min(height, bottom - top)
            vertical_overlap = (
                overlap_height / smaller_height
                if smaller_height > 0
                else 0.0
            )

            distance = abs(center - row_center)

            if (
                distance <= tolerance
                and vertical_overlap >= config.min_vertical_overlap
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

    return sorted(lines, key=lambda line: line_statistics(line)[0])


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
            "text": " ".join(word["text"] for word in group),
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
    settings = replace(config or LayoutConfig(), **options)
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
                for reason in (word.get("mergeReviewReasons") or [])
            }
        )

        result.append(
            {
                "text": " ".join(word["text"] for word in line),
                "cells": [detail["text"] for detail in details],
                "bbox": bounds(line),
                "words": [dict(word) for word in line],
                "cellDetails": details,
                "sources": sorted({word["source"] for word in line}),
                "needsReview": bool(reasons),
                "reviewReasons": reasons,
            }
        )

    return result