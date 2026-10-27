"""PDF 내장 텍스트와 OCR 단어를 좌표 기반으로 병합한다."""

from __future__ import annotations

import math
import re
import statistics

from typing import Any


def normalize(value: object) -> str:
    """비교용 문자열을 만든다.

    숫자의 의미가 달라지지 않도록
    소수점, 부호, 백분율 기호는 보존한다.
    """

    source = str(
        value if value is not None else ""
    )

    return re.sub(
        r"\s+",
        "",
        source.casefold(),
    )


def prepare_word(
    word: dict[str, Any],
    *,
    default_source: str = "UNKNOWN",
) -> dict[str, Any] | None:
    """입력 단어의 좌표와 값을 표준 형식으로 변환한다."""

    if not isinstance(word, dict):
        return None

    source_text = str(
        word.get("text")
        if word.get("text") is not None
        else ""
    ).strip()

    if not source_text:
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

    coordinates = (
        left,
        top,
        right,
        bottom,
    )

    if not all(
        math.isfinite(value)
        for value in coordinates
    ):
        return None

    if right <= left or bottom <= top:
        return None

    confidence = None

    if word.get("confidence") is not None:
        try:
            candidate = float(
                word["confidence"]
            )

            if math.isfinite(candidate):
                confidence = candidate

        except (
            TypeError,
            ValueError,
            OverflowError,
        ):
            pass

    source = str(
        word.get("source")
        or default_source
    ).strip()

    result = dict(word)

    # 좌표 키를 한 가지 형식으로 통일한다.
    result.pop("x0", None)
    result.pop("x1", None)

    result.update(
        {
            "text": source_text,
            "left": left,
            "top": top,
            "right": right,
            "bottom": bottom,
            "source": source or default_source,
            "confidence": confidence,
        }
    )

    return result


def prepare_words(
    words: list[dict[str, Any]],
    *,
    default_source: str = "UNKNOWN",
) -> list[dict[str, Any]]:
    result = []

    for word in words:
        prepared = prepare_word(
            word,
            default_source=default_source,
        )

        if prepared is not None:
            result.append(prepared)

    return result


def has_broken_text(
    value: object,
) -> bool:
    """명시적인 대체 문자와 비정상 제어 문자를 확인한다.

    정상적인 문자로 잘못 추출된 경우까지
    자동으로 판별하는 기능은 아니다.
    """

    source = str(
        value if value is not None else ""
    )

    if "\ufffd" in source:
        return True

    return any(
        ord(character) < 32
        and not character.isspace()
        for character in source
    )


def word_area(
    word: dict[str, Any],
) -> float:
    width = max(
        0.0,
        word["right"] - word["left"],
    )
    height = max(
        0.0,
        word["bottom"] - word["top"],
    )

    return width * height


def intersection_area(
    first: dict[str, Any],
    second: dict[str, Any],
) -> float:
    left = max(
        first["left"],
        second["left"],
    )
    top = max(
        first["top"],
        second["top"],
    )
    right = min(
        first["right"],
        second["right"],
    )
    bottom = min(
        first["bottom"],
        second["bottom"],
    )

    if right <= left or bottom <= top:
        return 0.0

    return (
        (right - left)
        * (bottom - top)
    )


def overlap_ratio(
    a: dict[str, Any],
    b: dict[str, Any],
) -> float:
    """두 단어 중 작은 영역이 겹치는 비율을 반환한다."""

    first = prepare_word(a)
    second = prepare_word(b)

    if first is None or second is None:
        return 0.0

    smaller_area = min(
        word_area(first),
        word_area(second),
    )

    if smaller_area <= 0:
        return 0.0

    return (
        intersection_area(first, second)
        / smaller_area
    )


def union_area(
    rectangles: list[
        tuple[float, float, float, float]
    ],
) -> float:
    """겹치는 사각형의 면적을 중복 계산하지 않는다."""

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
        if right <= left:
            continue

        intervals = sorted(
            (top, bottom)
            for x0, top, x1, bottom in rectangles
            if x0 < right and x1 > left
        )

        if not intervals:
            continue

        start, end = intervals[0]
        covered_height = 0.0

        for top, bottom in intervals[1:]:
            if top <= end:
                end = max(end, bottom)
            else:
                covered_height += end - start
                start, end = top, bottom

        covered_height += end - start

        total += (
            (right - left)
            * covered_height
        )

    return total


def coverage_ratio(
    word: dict[str, Any],
    existing_words: list[dict[str, Any]],
) -> float:
    """입력 단어 영역이 기존 단어들에 덮인 비율을 계산한다."""

    area = word_area(word)

    if area <= 0:
        return 0.0

    intersections = []

    for existing in existing_words:
        left = max(
            word["left"],
            existing["left"],
        )
        top = max(
            word["top"],
            existing["top"],
        )
        right = min(
            word["right"],
            existing["right"],
        )
        bottom = min(
            word["bottom"],
            existing["bottom"],
        )

        if right > left and bottom > top:
            intersections.append(
                (
                    left,
                    top,
                    right,
                    bottom,
                )
            )

    return min(
        1.0,
        union_area(intersections) / area,
    )


def same_position_and_text(
    first: dict[str, Any],
    second: dict[str, Any],
    *,
    minimum_overlap: float = 0.35,
) -> bool:
    if (
        normalize(first["text"])
        != normalize(second["text"])
    ):
        return False

    smaller_area = min(
        word_area(first),
        word_area(second),
    )

    if smaller_area <= 0:
        return False

    return (
        intersection_area(first, second)
        / smaller_area
    ) >= minimum_overlap


def merge_words(
    native: list[dict[str, Any]],
    ocr: list[dict[str, Any]],
    *,
    coverage_threshold: float = 0.8,
    min_ocr_confidence: float | None = None,
) -> list[dict[str, Any]]:
    """내장 텍스트를 우선하고 OCR로 누락 영역을 보완한다.

    모든 입력 좌표는 같은 단위와 회전 방향이어야 한다.
    키 이름은 변환하지만 픽셀과 PDF 좌표 간 변환은 하지 않는다.
    """

    if not 0 < coverage_threshold <= 1:
        raise ValueError(
            "중복 영역 기준은 0보다 크고 1 이하여야 합니다."
        )

    if (
        min_ocr_confidence is not None
        and (
            not math.isfinite(min_ocr_confidence)
            or not 0 <= min_ocr_confidence <= 100
        )
    ):
        raise ValueError(
            "OCR 최소 신뢰도는 0~100이어야 합니다."
        )

    native_words = prepare_words(
        native,
        default_source="PDF_TEXT",
    )

    ocr_words = prepare_words(
        ocr,
        default_source="OCR",
    )

    result: list[dict[str, Any]] = []

    # 정상 내장 텍스트끼리도 같은 위치의 중복을 제거한다.
    for word in native_words:
        if has_broken_text(word["text"]):
            continue

        if any(
            same_position_and_text(
                word,
                existing,
            )
            for existing in result
        ):
            continue

        result.append(word)

    # 신뢰도가 높은 OCR 결과부터 처리한다.
    ordered_ocr = sorted(
        ocr_words,
        key=lambda word: (
            word["confidence"]
            if word["confidence"] is not None
            else -1.0
        ),
        reverse=True,
    )

    for word in ordered_ocr:
        if has_broken_text(word["text"]):
            continue

        confidence = word["confidence"]

        if min_ocr_confidence is not None:
            if (
                confidence is None
                or confidence < min_ocr_confidence
            ):
                continue

        # 내장 텍스트뿐 아니라 이미 추가한 OCR과도 비교한다.
        if any(
            same_position_and_text(
                word,
                existing,
            )
            for existing in result
        ):
            continue

        overlapping = [
            existing
            for existing in result
            if intersection_area(
                word,
                existing,
            ) > 0
        ]

        if (
            coverage_ratio(
                word,
                overlapping,
            )
            >= coverage_threshold
        ):
            continue

        candidate = dict(word)

        # 일부만 겹치는 경우에는 단어를 자르거나 버리지 않는다.
        # 대신 후속 단계에서 확인할 수 있도록 표시한다.
        if overlapping:
            candidate["mergeNeedsReview"] = True
            candidate["mergeReason"] = (
                "기존 단어와 일부 영역이 겹칩니다."
            )

        result.append(candidate)

    return sorted(
        result,
        key=lambda word: (
            (
                word["top"]
                + word["bottom"]
            ) / 2,
            word["left"],
        ),
    )


def row_statistics(
    words: list[dict[str, Any]],
) -> tuple[float, float, float, float]:
    center = statistics.median(
        (
            word["top"]
            + word["bottom"]
        ) / 2
        for word in words
    )

    height = statistics.median(
        word["bottom"] - word["top"]
        for word in words
    )

    top = statistics.median(
        word["top"]
        for word in words
    )

    bottom = statistics.median(
        word["bottom"]
        for word in words
    )

    return center, height, top, bottom


def vertical_overlap_ratio(
    word: dict[str, Any],
    row_top: float,
    row_bottom: float,
) -> float:
    intersection = max(
        0.0,
        min(
            word["bottom"],
            row_bottom,
        )
        - max(
            word["top"],
            row_top,
        ),
    )

    smaller_height = min(
        word["bottom"] - word["top"],
        row_bottom - row_top,
    )

    if smaller_height <= 0:
        return 0.0

    return intersection / smaller_height


def group_words_into_rows(
    words: list[dict[str, Any]],
    *,
    row_tolerance_factor: float,
) -> list[list[dict[str, Any]]]:
    """행과 단어의 높이를 비교해 같은 줄을 구성한다."""

    rows: list[
        list[dict[str, Any]]
    ] = []

    ordered = sorted(
        words,
        key=lambda word: (
            (
                word["top"]
                + word["bottom"]
            ) / 2,
            word["left"],
        ),
    )

    for word in ordered:
        center = (
            word["top"]
            + word["bottom"]
        ) / 2

        height = (
            word["bottom"]
            - word["top"]
        )

        best_row = None
        best_distance = float("inf")

        for row in rows:
            (
                row_center,
                row_height,
                row_top,
                row_bottom,
            ) = row_statistics(row)

            tolerance = max(
                2.0,
                min(
                    height,
                    row_height,
                ) * row_tolerance_factor,
            )

            distance = abs(
                center - row_center
            )

            overlap = vertical_overlap_ratio(
                word,
                row_top,
                row_bottom,
            )

            if (
                distance <= tolerance
                and overlap >= 0.4
                and distance < best_distance
            ):
                best_row = row
                best_distance = distance

        if best_row is None:
            rows.append([word])
        else:
            best_row.append(word)

    for row in rows:
        row.sort(
            key=lambda word: word["left"]
        )

    rows.sort(
        key=lambda row: row_statistics(row)[0]
    )

    return rows


def build_cells(
    words: list[dict[str, Any]],
    *,
    cell_gap_factor: float,
    min_cell_gap: float,
) -> list[dict[str, Any]]:
    """단어 간 간격으로 셀 후보를 구성한다."""

    if not words:
        return []

    character_widths = [
        (
            word["right"] - word["left"]
        ) / max(
            1,
            len(
                re.sub(
                    r"\s+",
                    "",
                    word["text"],
                )
            ),
        )
        for word in words
    ]

    gap_threshold = max(
        min_cell_gap,
        statistics.median(
            character_widths
        ) * cell_gap_factor,
    )

    groups: list[
        list[dict[str, Any]]
    ] = []

    current: list[
        dict[str, Any]
    ] = []

    current_right = None

    for word in words:
        if (
            current
            and current_right is not None
            and word["left"] - current_right
            > gap_threshold
        ):
            groups.append(current)
            current = []
            current_right = None

        current.append(word)

        current_right = (
            word["right"]
            if current_right is None
            else max(
                current_right,
                word["right"],
            )
        )

    if current:
        groups.append(current)

    return [
        {
            "text": " ".join(
                word["text"]
                for word in group
            ),
            "bbox": [
                min(
                    word["left"]
                    for word in group
                ),
                min(
                    word["top"]
                    for word in group
                ),
                max(
                    word["right"]
                    for word in group
                ),
                max(
                    word["bottom"]
                    for word in group
                ),
            ],
            "words": [
                dict(word)
                for word in group
            ],
        }
        for group in groups
    ]


def words_to_rows(
    words: list[dict[str, Any]],
    *,
    row_tolerance_factor: float = 0.4,
    cell_gap_factor: float = 2.0,
    min_cell_gap: float = 7.0,
) -> list[dict[str, Any]]:
    """단어 좌표에서 행과 셀 후보를 복원한다.

    빈 컬럼이나 여러 줄 설명을 확정적으로 복원하지는 않는다.
    셀 좌표와 단어 좌표를 함께 반환해 후속 검증에 사용한다.
    """

    if (
        not math.isfinite(row_tolerance_factor)
        or not 0 < row_tolerance_factor <= 1
    ):
        raise ValueError(
            "행 허용 오차 배율은 0보다 크고 1 이하여야 합니다."
        )

    if (
        not math.isfinite(cell_gap_factor)
        or cell_gap_factor <= 0
    ):
        raise ValueError(
            "셀 간격 배율은 0보다 커야 합니다."
        )

    if (
        not math.isfinite(min_cell_gap)
        or min_cell_gap < 0
    ):
        raise ValueError(
            "최소 셀 간격은 0 이상이어야 합니다."
        )

    prepared = prepare_words(words)

    if not prepared:
        return []

    rows = group_words_into_rows(
        prepared,
        row_tolerance_factor=row_tolerance_factor,
    )

    output = []

    for row in rows:
        cell_details = build_cells(
            row,
            cell_gap_factor=cell_gap_factor,
            min_cell_gap=min_cell_gap,
        )

        cells = [
            cell["text"]
            for cell in cell_details
        ]

        merge_reasons = sorted(
            {
                str(
                    word.get("mergeReason")
                    or "단어 중복 여부 확인 필요"
                )
                for word in row
                if word.get("mergeNeedsReview")
            }
        )

        output.append(
            {
                # 기존 파서와의 연결 형식을 유지한다.
                "text": " | ".join(cells),
                "cells": cells,
                "bbox": [
                    min(
                        word["left"]
                        for word in row
                    ),
                    min(
                        word["top"]
                        for word in row
                    ),
                    max(
                        word["right"]
                        for word in row
                    ),
                    max(
                        word["bottom"]
                        for word in row
                    ),
                ],
                "sources": sorted(
                    {
                        word["source"]
                        for word in row
                    }
                ),
                "words": [
                    dict(word)
                    for word in row
                ],
                "cellDetails": cell_details,
                "needsReview": bool(
                    merge_reasons
                ),
                "reviewReasons": merge_reasons,
            }
        )

    return output