"""PDF 내장 텍스트와 OCR 결과를 결합해 기존 거래 파서에 전달한다."""

from __future__ import annotations

import math
import re
import statistics

from pathlib import Path
from typing import Any, Callable

import fitz
import pytesseract

from PIL import Image
from pytesseract import Output


MAX_PAGES = 50

DEFAULT_DPI = 300
DEFAULT_LANGUAGE = "eng+kor"

DEFAULT_MIN_CONFIDENCE = 20.0
DEFAULT_OCR_TIMEOUT = 120.0

# 페이지 크기에 따른 과도한 이미지 생성을 제한한다.
MAX_RENDER_PIXELS = 40_000_000

DEFAULT_TESSDATA_DIR = (
    Path(__file__).resolve().parent / "tessdata"
)

CategoryResolver = Callable[
    [dict[str, Any]],
    str | None,
]


def text(value: object) -> str:
    return str(
        value if value is not None else ""
    ).strip()


def validate_pdf_bytes(pdf_bytes: bytes) -> None:
    if (
        not isinstance(pdf_bytes, bytes)
        or not pdf_bytes.startswith(b"%PDF-")
    ):
        raise ValueError(
            "올바른 PDF 파일이 아닙니다."
        )


def resolve_tessdata_dir(
    tessdata_dir: str | Path | None,
    language: str,
) -> Path:
    """언어 학습 데이터 경로와 필요한 파일을 확인한다."""

    if not re.fullmatch(
        r"[A-Za-z0-9_]+(?:\+[A-Za-z0-9_]+)*",
        language,
    ):
        raise ValueError(
            "OCR 언어 설정이 올바르지 않습니다."
        )

    directory = (
        Path(tessdata_dir).expanduser()
        if tessdata_dir is not None
        else DEFAULT_TESSDATA_DIR
    ).resolve()

    if not directory.is_dir():
        raise ValueError(
            f"tessdata 폴더를 찾을 수 없습니다: {directory}"
        )

    missing = [
        f"{name}.traineddata"
        for name in language.split("+")
        if not (
            directory / f"{name}.traineddata"
        ).is_file()
    ]

    if missing:
        raise ValueError(
            "OCR 언어 학습 데이터가 없습니다: "
            + ", ".join(missing)
        )

    return directory


def native_words(
    page: fitz.Page,
) -> list[dict[str, Any]]:
    """PDF에 저장된 단어와 좌표를 가져온다."""

    result = []

    for entry in page.get_text(
        "words",
        sort=True,
    ):
        if len(entry) < 5:
            continue

        source = text(entry[4])

        if not source:
            continue

        # 깨진 문자 데이터는 OCR로 보완하도록 제외한다.
        if "\ufffd" in source:
            continue

        rectangle = fitz.Rect(
            float(entry[0]),
            float(entry[1]),
            float(entry[2]),
            float(entry[3]),
        )

        # 내장 텍스트 좌표를 렌더링 이미지의 회전 방향에 맞춘다.
        rectangle = (
            rectangle * page.rotation_matrix
        )

        if (
            rectangle.width <= 0
            or rectangle.height <= 0
        ):
            continue

        result.append(
            {
                "text": source,
                "x0": float(rectangle.x0),
                "top": float(rectangle.y0),
                "x1": float(rectangle.x1),
                "bottom": float(rectangle.y1),
                "source": "PDF_TEXT",
                "confidence": None,
            }
        )

    return result


def render_page(
    page: fitz.Page,
    dpi: int,
) -> Image.Image:
    """페이지를 OCR용 이미지로 변환한다."""

    width = float(page.rect.width)
    height = float(page.rect.height)

    if width <= 0 or height <= 0:
        raise ValueError(
            "PDF 페이지 크기가 올바르지 않습니다."
        )

    scale = dpi / 72.0

    estimated_width = math.ceil(
        width * scale
    )
    estimated_height = math.ceil(
        height * scale
    )

    estimated_pixels = (
        estimated_width * estimated_height
    )

    if estimated_pixels > MAX_RENDER_PIXELS:
        scale *= math.sqrt(
            MAX_RENDER_PIXELS
            / estimated_pixels
        )

    pixmap = page.get_pixmap(
        matrix=fitz.Matrix(scale, scale),
        colorspace=fitz.csRGB,
        alpha=False,
    )

    return Image.frombytes(
        "RGB",
        (pixmap.width, pixmap.height),
        pixmap.samples,
    )


def ocr_words(
    page: fitz.Page,
    *,
    tessdata_dir: Path,
    language: str,
    dpi: int,
    min_confidence: float,
    timeout: float,
    psm: int,
) -> list[dict[str, Any]]:
    """페이지 전체를 OCR하고 이미지 좌표를 PDF 좌표로 변환한다."""

    image = render_page(
        page,
        dpi,
    )

    try:
        config = (
            f'--tessdata-dir "{tessdata_dir}" '
            f"--psm {psm}"
        )

        data = pytesseract.image_to_data(
            image,
            lang=language,
            config=config,
            output_type=Output.DICT,
            timeout=timeout,
        )

        # DPI로만 역산하지 않고 실제 이미지 크기를 사용한다.
        # 렌더링 크기 제한이나 반올림이 있어도 좌표가 일치한다.
        scale_x = (
            float(page.rect.width)
            / image.width
        )
        scale_y = (
            float(page.rect.height)
            / image.height
        )

        result = []

        for index, value in enumerate(
            data.get("text", [])
        ):
            source = text(value)

            if not source:
                continue

            try:
                confidence = float(
                    data["conf"][index]
                )

                left = float(
                    data["left"][index]
                )
                top = float(
                    data["top"][index]
                )
                width = float(
                    data["width"][index]
                )
                height = float(
                    data["height"][index]
                )
            except (
                KeyError,
                IndexError,
                TypeError,
                ValueError,
            ):
                continue

            if (
                not math.isfinite(confidence)
                or confidence < min_confidence
                or width <= 0
                or height <= 0
            ):
                continue

            result.append(
                {
                    "text": source,
                    "x0": left * scale_x,
                    "top": top * scale_y,
                    "x1": (
                        left + width
                    ) * scale_x,
                    "bottom": (
                        top + height
                    ) * scale_y,
                    "source": "OCR",
                    "confidence": confidence,
                }
            )

        return result

    finally:
        image.close()


def word_area(
    word: dict[str, Any],
) -> float:
    width = max(
        0.0,
        float(word["x1"])
        - float(word["x0"]),
    )

    height = max(
        0.0,
        float(word["bottom"])
        - float(word["top"]),
    )

    return width * height


def intersection_area(
    first: dict[str, Any],
    second: dict[str, Any],
) -> float:
    width = max(
        0.0,
        min(
            float(first["x1"]),
            float(second["x1"]),
        )
        - max(
            float(first["x0"]),
            float(second["x0"]),
        ),
    )

    height = max(
        0.0,
        min(
            float(first["bottom"]),
            float(second["bottom"]),
        )
        - max(
            float(first["top"]),
            float(second["top"]),
        ),
    )

    return width * height


def normalized_word(
    value: object,
) -> str:
    return re.sub(
        r"\s+",
        "",
        text(value),
    ).casefold()


def is_duplicate_word(
    first: dict[str, Any],
    second: dict[str, Any],
) -> bool:
    """단어의 위치와 문자열을 함께 확인한다."""

    first_area = word_area(first)
    second_area = word_area(second)

    if first_area <= 0 or second_area <= 0:
        return False

    intersection = intersection_area(
        first,
        second,
    )

    if intersection <= 0:
        return False

    smaller_area = min(
        first_area,
        second_area,
    )

    overlap = (
        intersection / smaller_area
    )

    if overlap >= 0.65:
        return True

    # 같은 문자가 거의 같은 위치에 있으면
    # 박스 크기에 차이가 있어도 중복으로 판단한다.
    return (
        overlap >= 0.35
        and normalized_word(
            first["text"]
        )
        == normalized_word(
            second["text"]
        )
    )


def merge_words(
    embedded: list[dict[str, Any]],
    scanned: list[dict[str, Any]],
) -> list[dict[str, Any]]:
    """내장 텍스트를 우선하고 비어 있는 위치를 OCR로 보완한다."""

    merged = [
        dict(word)
        for word in embedded
    ]

    # 신뢰도가 높은 OCR 단어부터 비교한다.
    ordered_scanned = sorted(
        scanned,
        key=lambda word: float(
            word.get("confidence") or 0
        ),
        reverse=True,
    )

    for word in ordered_scanned:
        if any(
            is_duplicate_word(
                word,
                existing,
            )
            for existing in merged
        ):
            continue

        merged.append(dict(word))

    return sorted(
        merged,
        key=lambda word: (
            float(word["top"]),
            float(word["x0"]),
        ),
    )


def group_lines(
    words: list[dict[str, Any]],
) -> list[list[dict[str, Any]]]:
    """단어의 세로 중심을 기준으로 같은 행을 묶는다."""

    lines: list[
        list[dict[str, Any]]
    ] = []

    ordered = sorted(
        words,
        key=lambda word: (
            (
                float(word["top"])
                + float(word["bottom"])
            ) / 2,
            float(word["x0"]),
        ),
    )

    for word in ordered:
        center = (
            float(word["top"])
            + float(word["bottom"])
        ) / 2

        height = (
            float(word["bottom"])
            - float(word["top"])
        )

        selected = None
        smallest_distance = None

        for line in reversed(lines):
            line_center = statistics.median(
                (
                    float(item["top"])
                    + float(item["bottom"])
                ) / 2
                for item in line
            )

            line_height = statistics.median(
                float(item["bottom"])
                - float(item["top"])
                for item in line
            )

            tolerance = max(
                2.0,
                min(
                    height,
                    line_height,
                ) * 0.4,
            )

            distance = abs(
                center - line_center
            )

            if distance <= tolerance:
                if (
                    smallest_distance is None
                    or distance < smallest_distance
                ):
                    selected = line
                    smallest_distance = distance

            if line_center < center - max(
                height,
                line_height,
            ) * 2:
                break

        if selected is None:
            lines.append([word])
        else:
            selected.append(word)

    for line in lines:
        line.sort(
            key=lambda word: float(
                word["x0"]
            )
        )

    lines.sort(
        key=lambda line: statistics.median(
            float(word["top"])
            for word in line
        )
    )

    return lines


def line_to_cells(
    words: list[dict[str, Any]],
    *,
    cell_gap_factor: float,
) -> list[str]:
    """단어 사이의 큰 간격을 이용해 셀 후보를 나눈다."""

    if not words:
        return []

    heights = [
        float(word["bottom"])
        - float(word["top"])
        for word in words
    ]

    threshold = max(
        6.0,
        statistics.median(heights)
        * cell_gap_factor,
    )

    cells = []
    current = [
        text(words[0]["text"])
    ]

    for previous, word in zip(
        words,
        words[1:],
    ):
        gap = (
            float(word["x0"])
            - float(previous["x1"])
        )

        if gap > threshold:
            cells.append(
                " ".join(current)
            )
            current = [
                text(word["text"])
            ]
        else:
            current.append(
                text(word["text"])
            )

    cells.append(
        " ".join(current)
    )

    return cells


def words_to_rows(
    words: list[dict[str, Any]],
    *,
    cell_gap_factor: float = 0.7,
) -> list[dict[str, Any]]:
    """단어 좌표로 행과 셀 후보를 복원한다."""

    rows = []

    for line in group_lines(words):
        cells = line_to_cells(
            line,
            cell_gap_factor=cell_gap_factor,
        )

        source = " ".join(
            text(word["text"])
            for word in line
        )

        if not source:
            continue

        rows.append(
            {
                "text": source,
                "cells": cells,
                "x0": min(
                    float(word["x0"])
                    for word in line
                ),
                "top": min(
                    float(word["top"])
                    for word in line
                ),
                "x1": max(
                    float(word["x1"])
                    for word in line
                ),
                "bottom": max(
                    float(word["bottom"])
                    for word in line
                ),
                # 추후 정밀한 컬럼 복원에 사용할 수 있도록
                # 개별 단어 좌표도 보존한다.
                "words": [
                    dict(word)
                    for word in line
                ],
            }
        )

    return rows


def extract_pdf_layout(
    pdf_bytes: bytes,
    *,
    tessdata_dir: str | Path | None = None,
    language: str = DEFAULT_LANGUAGE,
    dpi: int = DEFAULT_DPI,
    min_confidence: float = DEFAULT_MIN_CONFIDENCE,
    timeout: float = DEFAULT_OCR_TIMEOUT,
    psm: int = 3,
    cell_gap_factor: float = 0.7,
) -> dict[str, Any]:
    """전체 페이지를 OCR하고 PDF 텍스트와 결합한다."""

    validate_pdf_bytes(pdf_bytes)

    if (
        type(dpi) is not int
        or not 72 <= dpi <= 600
    ):
        raise ValueError(
            "OCR 해상도는 72~600 DPI여야 합니다."
        )

    if (
        not math.isfinite(min_confidence)
        or not 0 <= min_confidence <= 100
    ):
        raise ValueError(
            "OCR 신뢰도는 0~100이어야 합니다."
        )

    if (
        not math.isfinite(timeout)
        or timeout <= 0
    ):
        raise ValueError(
            "OCR 제한 시간은 0보다 커야 합니다."
        )

    # 단어 위치가 필요한 모드만 허용한다.
    if (
        type(psm) is not int
        or psm not in {
            3, 4, 5, 6, 7, 8,
            9, 10, 11, 12, 13,
        }
    ):
        raise ValueError(
            "지원하지 않는 OCR 페이지 분석 모드입니다."
        )

    if (
        not math.isfinite(cell_gap_factor)
        or cell_gap_factor <= 0
    ):
        raise ValueError(
            "셀 간격 배율은 0보다 커야 합니다."
        )

    pages = []

    with fitz.open(
        stream=pdf_bytes,
        filetype="pdf",
    ) as document:
        if (
            document.needs_pass
            or document.is_encrypted
        ):
            raise ValueError(
                "암호화된 PDF는 지원하지 않습니다."
            )

        if len(document) > MAX_PAGES:
            raise ValueError(
                "PDF 페이지 수가 제한을 초과했습니다."
            )

        data_directory = resolve_tessdata_dir(
            tessdata_dir,
            language,
        )

        for index, page in enumerate(document):
            page_number = index + 1

            # 내장 텍스트와 OCR 단어를
            # 동일한 좌표 기준으로 추출한다.
            embedded = native_words(page)

            try:
                scanned = ocr_words(
                    page,
                    tessdata_dir=data_directory,
                    language=language,
                    dpi=dpi,
                    min_confidence=min_confidence,
                    timeout=timeout,
                    psm=psm,
                )
            except pytesseract.TesseractNotFoundError as exc:
                raise RuntimeError(
                    "Tesseract 실행 파일을 찾을 수 없습니다. "
                    "설치 경로와 PATH 설정을 확인해 주세요."
                ) from exc
            except pytesseract.TesseractError as exc:
                raise RuntimeError(
                    f"{page_number}페이지 OCR 실행에 실패했습니다: "
                    f"{exc}"
                ) from exc
            except RuntimeError as exc:
                raise RuntimeError(
                    f"{page_number}페이지 OCR 처리에 실패했습니다: "
                    f"{exc}"
                ) from exc

            merged = merge_words(
                embedded,
                scanned,
            )

            rows = words_to_rows(
                merged,
                cell_gap_factor=cell_gap_factor,
            )

            pages.append(
                {
                    "pageNumber": page_number,
                    "method": "PDF_TEXT_AND_OCR",
                    "ocrUsed": True,
                    "text": "\n".join(
                        row["text"]
                        for row in rows
                    ),
                    "rows": rows,
                    "nativeWordCount": len(
                        embedded
                    ),
                    "ocrWordCount": len(
                        scanned
                    ),
                    "mergedWordCount": len(
                        merged
                    ),
                }
            )

    return {
        "totalPages": len(pages),
        "ocrPages": pages,
    }


def analyze_pdf_with_ocr(
    pdf_bytes: bytes,
    *,
    tessdata_dir: str | Path | None = None,
    language: str = DEFAULT_LANGUAGE,
    dpi: int = DEFAULT_DPI,
    min_confidence: float = DEFAULT_MIN_CONFIDENCE,
    timeout: float = DEFAULT_OCR_TIMEOUT,
    psm: int = 3,
    cell_gap_factor: float = 0.7,
    header_aliases: dict[
        str,
        list[str],
    ] | None = None,
    category_resolver: CategoryResolver | None = None,
) -> dict[str, Any]:
    """복원한 PDF 구조를 기존 거래 파서에 전달한다."""

    from .pdf_parser import extract_transactions

    layout = extract_pdf_layout(
        pdf_bytes,
        tessdata_dir=tessdata_dir,
        language=language,
        dpi=dpi,
        min_confidence=min_confidence,
        timeout=timeout,
        psm=psm,
        cell_gap_factor=cell_gap_factor,
    )

    # 앞서 제시한 범용 파서의 인자 구조에 맞춘 연동이다.
    result = extract_transactions(
        pdf_bytes,
        ocr_pages=layout["ocrPages"],
        header_aliases=header_aliases,
        category_resolver=category_resolver,
    )

    # 거래 추출 결과와 별개로
    # 실제 OCR 수행 내역을 제공한다.
    result["ocrPageResults"] = [
        {
            "pageNumber": page["pageNumber"],
            "method": page["method"],
            "ocrUsed": page["ocrUsed"],
            "nativeWordCount": page[
                "nativeWordCount"
            ],
            "ocrWordCount": page[
                "ocrWordCount"
            ],
            "mergedWordCount": page[
                "mergedWordCount"
            ],
        }
        for page in layout["ocrPages"]
    ]

    return result