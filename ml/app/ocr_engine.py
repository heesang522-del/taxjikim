"""PDF 페이지에 OCR을 적용하고 단어 좌표를 반환한다."""

from math import ceil
from pathlib import Path
from typing import Any

import pymupdf

TESSDATA_DIR = Path(__file__).resolve().parents[1] / "tessdata"
OCR_DPI = 200
MAX_RENDERED_PIXELS = 12_000_000


class OcrUnavailableError(RuntimeError):
    pass


def extract_words(textpage: pymupdf.TextPage) -> list[dict[str, Any]]:
    return [
        {
            "text": text,
            "x0": x0,
            "top": top,
            "x1": x1,
            "bottom": bottom,
        }
        for x0, top, x1, bottom, text, *_ in textpage.extractWORDS()
    ]


def recognize_words(page: pymupdf.Page) -> list[dict[str, Any]]:
    scale = OCR_DPI / 72
    width = ceil(page.rect.width * scale)
    height = ceil(page.rect.height * scale)

    if width * height > MAX_RENDERED_PIXELS:
        raise ValueError("OCR 이미지 크기가 허용 범위를 벗어났습니다.")

    for language in ("kor", "eng"):
        if not (TESSDATA_DIR / f"{language}.traineddata").is_file():
            raise OcrUnavailableError("OCR 언어 파일이 없습니다.")

    # 한 번 인식한 결과에서 단어와 좌표를 함께 사용한다.
    try:
        textpage = page.get_textpage_ocr(
            language="kor+eng",
            dpi=OCR_DPI,
            full=True,
            tessdata=str(TESSDATA_DIR),
        )
    except RuntimeError as exc:
        raise OcrUnavailableError("OCR 실행에 실패했습니다.") from exc

    return extract_words(textpage)