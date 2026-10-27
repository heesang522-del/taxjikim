"""Spring OCR 결과를 받아 PDF 거래를 분석한다."""

from __future__ import annotations

import json
import logging
import math
import statistics

from typing import Annotated, Any

from fastapi import FastAPI, File, Form, HTTPException, UploadFile

from .pdf_parser import extract_transactions
from . import pdf_parser
from .expense_classifier import classify_descriptions


logger = logging.getLogger("uvicorn.error")

MAX_FILE_BYTES = 6 * 1024 * 1024
MAX_PAGES = 50

# Spring이 전달하는 PDF 포인트 좌표 기준의 행 묶음 허용 간격이다.
ROW_TOP_TOLERANCE = 3.0
def load_expense_model() -> dict:
    """전체 데이터로 학습한 지출 모델과 전처리 파이프라인을 읽는다."""
    try:
        report = json.loads((MODEL_DIR / "model_report.json").read_text(encoding="utf-8"))
        selected = report["selected_model"]
        if selected not in ("logistic_regression", "linear_svc"):
            raise ValueError("선택한 모델 이름이 올바르지 않습니다.")
        # 학습 코드가 로컬에 생성한 파일만 읽는다. 서버에서 다시 학습하지 않는다.
        return joblib.load(MODEL_DIR / f"{selected}_bundle.pkl")
    except (FileNotFoundError, KeyError, ValueError) as exc:
        raise RuntimeError(
            "지출 모델을 먼저 준비해주세요: python scripts/train_expense.py"
        ) from exc

app = FastAPI(
    title="TaxJiKim ML API",
    version="1.0.0",
)


@app.get("/")
def root() -> dict[str, str]:
    return {
        "service": "TaxJiKim ML API",
        "status": "running",
    }


@app.get("/health")
def health() -> dict[str, str]:
    return {"status": "UP"}


def input_error(message: str) -> HTTPException:
    return HTTPException(
        status_code=400,
        detail={
            "code": "VALIDATION_ERROR",
            "message": message,
        },
    )


def valid_coordinate(value: object) -> bool:
    return (
        type(value) in (int, float)
        and math.isfinite(value)
        and value >= 0
    )


def words_to_rows(words: list[dict[str, Any]]) -> list[dict[str, Any]]:
    """Spring OCR 단어를 세로 위치와 가로 간격으로 묶는다."""

    lines: list[dict[str, Any]] = []

    # Spring은 단어의 높이를 보내지 않으므로 top 좌표로 행을 구분한다.
    for word in sorted(words, key=lambda item: (item["top"], item["x0"])):
        nearest = min(
            lines,
            key=lambda line: abs(line["top"] - word["top"]),
            default=None,
        )

        if (
            nearest is None
            or abs(nearest["top"] - word["top"]) > ROW_TOP_TOLERANCE
        ):
            lines.append({
                "top": word["top"],
                "words": [word],
            })
            continue

        nearest["words"].append(word)
        nearest["top"] = statistics.median(
            item["top"] for item in nearest["words"]
        )

    result = []

    for line in sorted(lines, key=lambda item: item["top"]):
        ordered = sorted(line["words"], key=lambda item: item["x0"])

        char_widths = [
            (word["x1"] - word["x0"]) / max(1, len(word["text"]))
            for word in ordered
        ]

        # 같은 셀의 단어는 합치고, 큰 가로 간격은 열 경계로 취급한다.
        cell_gap = max(7.0, statistics.median(char_widths) * 2.0)
        cells: list[str] = []
        group: list[dict[str, Any]] = []

        for word in ordered:
            if group and word["x0"] - group[-1]["x1"] > cell_gap:
                cells.append(" ".join(item["text"] for item in group))
                group = []

            group.append(word)

        if group:
            cells.append(" ".join(item["text"] for item in group))

        result.append({
            "text": " | ".join(cells),
            "cells": cells,
        })

    return result


def parse_ocr_pages(value: str) -> list[dict[str, Any]]:
    """Spring의 ocrPages JSON을 검증하고 기존 파서 입력으로 변환한다."""

    try:
        pages = json.loads(value)
    except json.JSONDecodeError as exc:
        raise input_error("ocrPages는 올바른 JSON 배열이어야 합니다.") from exc

    if not isinstance(pages, list):
        raise input_error("ocrPages는 배열이어야 합니다.")

    if len(pages) > MAX_PAGES:
        raise input_error("OCR 페이지 수가 허용 범위를 초과했습니다.")

    result = []
    seen_pages: set[int] = set()

    for page in pages:
        if not isinstance(page, dict):
            raise input_error("OCR 페이지 형식이 올바르지 않습니다.")

        page_number = page.get("pageNumber")

        if (
            type(page_number) is not int
            or not 1 <= page_number <= MAX_PAGES
            or page_number in seen_pages
        ):
            raise input_error("OCR 페이지 번호가 잘못되었거나 중복되었습니다.")

        seen_pages.add(page_number)

        text = page.get("text", "")
        words = page.get("words") or []

        if not isinstance(text, str) or not isinstance(words, list):
            raise input_error("OCR 텍스트 또는 단어 목록 형식이 잘못되었습니다.")

        validated_words = []

        for word in words:
            if not isinstance(word, dict):
                raise input_error("OCR 단어 형식이 올바르지 않습니다.")

            word_text = word.get("text")

            if not isinstance(word_text, str):
                raise input_error("OCR 단어의 text는 문자열이어야 합니다.")

            if not all(
                valid_coordinate(word.get(key))
                for key in ("x0", "x1", "top")
            ):
                raise input_error("OCR 단어 좌표가 올바르지 않습니다.")

            if word["x1"] < word["x0"]:
                raise input_error("OCR 단어의 가로 좌표가 올바르지 않습니다.")

            if word_text.strip():
                validated_words.append({
                    "text": word_text.strip(),
                    "x0": word["x0"],
                    "x1": word["x1"],
                    "top": word["top"],
                })

        if not validated_words and not text.strip():
            raise input_error(
                f"{page_number}페이지에서 OCR 텍스트를 인식하지 못했습니다."
            )

        normalized_page = {
            "pageNumber": page_number,
            "text": text,
        }

        # 단어 좌표가 있으면 행·열을 복원한다.
        # 좌표가 없으면 기존 파서가 text의 줄바꿈을 사용한다.
        if validated_words:
            normalized_page["rows"] = words_to_rows(validated_words)

        result.append(normalized_page)

    return result


@app.post("/api/pdf/analyze")
def analyze_pdf(
    file: Annotated[UploadFile, File()],
    ocr_pages_json: Annotated[str, Form(alias="ocrPages")],
) -> dict[str, Any]:
    try:
        # 제한보다 한 바이트만 더 읽어 과도한 파일 읽기를 방지한다.
        data = file.file.read(MAX_FILE_BYTES + 1)

        if not data:
            raise input_error("PDF 파일이 비어 있습니다.")

        if len(data) > MAX_FILE_BYTES:
            raise HTTPException(
                status_code=413,
                detail="PDF 파일은 6MB 이하여야 합니다.",
            )

        if not data.startswith(b"%PDF-"):
            raise input_error("올바른 PDF 파일이 아닙니다.")

        pages = parse_ocr_pages(ocr_pages_json)

        # OCR은 Spring에서 수행한다.
        # Python에서는 pdfplumber 기반 기존 파서에 결과만 전달한다.
        result = extract_transactions(
            data,
            ocr_pages=pages,
        )

        if result.get("totalPages", 0) > MAX_PAGES:
            raise input_error("PDF 페이지 수가 허용 범위를 초과했습니다.")

        logger.info(
            "PDF 분석 완료: pages=%s, extracted=%s, parsed=%s",
            result.get("totalPages"),
            result.get("extractedCount"),
            result.get("parsedCount"),
        )

        return {
            "fileName": file.filename,
            **result,
        }

    except HTTPException:
        raise

    except ValueError as exc:
        raise input_error("PDF 내용 또는 OCR 결과를 확인해주세요.") from exc

    except Exception as exc:
        # 금융 거래 원문이나 파일명은 오류 로그에 남기지 않는다.
        logger.error(
            "PDF 분석 실패: exceptionType=%s",
            type(exc).__name__,
        )
        raise HTTPException(
            status_code=500,
            detail={
                "code": "PDF_ANALYSIS_FAILED",
                "message": "PDF 분석에 실패했습니다.",
            },
        ) from exc

    finally:
        file.file.close()
       