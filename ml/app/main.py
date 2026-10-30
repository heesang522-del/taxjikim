"""Spring OCR 결과를 받아 날짜별 거래를 분석한다."""

from __future__ import annotations

import json
import logging
import math

from collections.abc import AsyncIterator
from contextlib import asynccontextmanager
from pathlib import Path
from threading import Event, Thread
from typing import Annotated, Any

import joblib
from pdfminer.pdfdocument import PDFPasswordIncorrect
from pdfminer.pdfparser import PDFSyntaxError
from pdfplumber.utils.exceptions import PdfminerException
from fastapi import (
    FastAPI,
    File,
    Form,
    HTTPException,
    UploadFile,
)

from . import pdf_parser
from .expense_classifier import classify_descriptions

# 프로젝트 루트와 ml 폴더 중 어디서 서버를 실행해도 같은 스케줄러를 사용한다.
if __package__ == "ml.app":
    from ..scripts.retrain_scheduler import run_scheduler
else:
    from scripts.retrain_scheduler import run_scheduler


logger = logging.getLogger("uvicorn.error")

MAX_FILE_BYTES = 6 * 1024 * 1024
MAX_PAGES = 50

MODEL_DIR = (
    Path(__file__).resolve().parent.parent
    / "models"
    / "expense_classifier"
)

def load_expense_model() -> dict:
    """기존 지출 모델 로딩 함수를 유지한다."""

    try:
        report = json.loads(
            (MODEL_DIR / "model_report.json").read_text(
                encoding="utf-8"
            )
        )

        selected = report["selected_model"]

        if selected not in (
            "logistic_regression",
            "linear_svc",
        ):
            raise ValueError(
                "선택한 모델 이름이 올바르지 않습니다."
            )

        return joblib.load(
            MODEL_DIR / f"{selected}_bundle.pkl"
        )

    except (
        FileNotFoundError,
        KeyError,
        ValueError,
    ) as exc:
        raise RuntimeError(
            "지출 모델을 먼저 준비해주세요: "
            "python scripts/train_expense.py"
        ) from exc


# 서버 시작 시 모델과 스케줄러를 준비하고 종료 시 스케줄러를 정리한다.
@asynccontextmanager
async def lifespan(app: FastAPI) -> AsyncIterator[None]:
    logger.info(
        "사용 중인 PDF 파서: %s",
        Path(pdf_parser.__file__).resolve(),
    )
    app.state.expense_model = load_expense_model()
    logger.info("지출 분류 모델: %s", app.state.expense_model["model_name"])

    # 요청을 처리하는 서버와 별도로 스케줄러를 실행한다.
    stop_event = Event()
    worker = Thread(target=run_scheduler, args=(stop_event,))
    worker.start()
    logger.info("재학습 스케줄러 시작")

    try:
        yield
    finally:
        # 진행 중인 학습은 끝날 때까지 기다리고, 다음 학습은 시작하지 않는다.
        stop_event.set()
        worker.join()
        logger.info("재학습 스케줄러 종료")


app = FastAPI(
    title="TaxJiKim ML API",
    version="1.0.0",
    lifespan=lifespan,
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


def parse_ocr_pages(
    value: str | None,
) -> list[dict[str, Any]]:
    if value is None:
        return []

    try:
        pages = json.loads(value)

    except (json.JSONDecodeError, TypeError) as exc:
        raise input_error(
            "ocrPages는 올바른 JSON 배열이어야 합니다."
        ) from exc

    if (
        not isinstance(pages, list)
        or len(pages) > MAX_PAGES
    ):
        raise input_error(
            "OCR 페이지 목록을 확인해주세요."
        )

    result = []
    seen = set()

    for page in pages:
        if not isinstance(page, dict):
            raise input_error(
                "OCR 페이지 형식이 올바르지 않습니다."
            )

        number = page.get("pageNumber")

        if (
            type(number) is not int
            or not 1 <= number <= MAX_PAGES
            or number in seen
            or not isinstance(page.get("text"), str)
            or type(page.get("ocrUsed")) is not bool
        ):
            raise input_error(
                "OCR 페이지 번호가 잘못되었거나 중복되었습니다."
            )

        # 단어 목록의 형식을 먼저 확인하고, 좌표 검증은 아래에서 한 번만 수행한다.
        words = page.get("words")
        if words is not None and not isinstance(words, list):
            raise input_error(
                "OCR 단어 목록은 배열이어야 합니다."
            )

        seen.add(number)

        source = page.get("text") or ""
        words = page.get("words") or []
        rows = page.get("rows") or []

        if (
            not isinstance(source, str)
            or not isinstance(words, list)
            or not isinstance(rows, list)
        ):
            raise input_error(
                "OCR의 text, words, rows 형식을 확인해주세요."
            )

        checked_words = []

        for word in words:
            if (
                not isinstance(word, dict)
                or not isinstance(word.get("text"), str)
            ):
                raise input_error(
                    "OCR 단어의 text는 문자열이어야 합니다."
                )

            coordinates = [
                word.get(key)
                for key in ("x0", "x1", "top")
            ]

            if not all(
                type(coordinate) in (int, float)
                and math.isfinite(coordinate)
                and coordinate >= 0
                for coordinate in coordinates
            ):
                raise input_error(
                    "OCR 단어 좌표가 올바르지 않습니다."
                )

            if word["x1"] <= word["x0"]:
                raise input_error(
                    "OCR 단어의 가로 좌표가 올바르지 않습니다."
                )

            if word["text"].strip():
                checked_words.append(
                    {
                        "text": word["text"].strip(),
                        "x0": word["x0"],
                        "x1": word["x1"],
                        "top": word["top"],
                    }
                )

        checked_rows = []

        for row in rows:
            if not isinstance(row, dict):
                raise input_error(
                    "OCR 행 형식이 올바르지 않습니다."
                )

            cells = row.get("cells")

            if cells is not None and (
                not isinstance(cells, list)
                or any(
                    cell is not None
                    and not isinstance(cell, str)
                    for cell in cells
                )
            ):
                raise input_error(
                    "OCR cells는 문자열 목록이어야 합니다."
                )

            for key in ("text", "rawText"):
                if (
                    key in row
                    and not isinstance(row[key], str)
                ):
                    raise input_error(
                        "OCR 행의 텍스트는 문자열이어야 합니다."
                    )

            reasons = row.get("reviewReasons") or []

            if (
                not isinstance(reasons, list)
                or any(
                    not isinstance(reason, str)
                    for reason in reasons
                )
            ):
                raise input_error(
                    "OCR 검토 사유는 문자열 목록이어야 합니다."
                )

            checked_rows.append(
                {
                    "cells": [
                        cell or ""
                        for cell in (cells or [])
                    ],
                    "text": (
                        row.get("text")
                        or row.get("rawText")
                        or ""
                    ),
                    "needsReview": bool(
                        row.get("needsReview")
                    ),
                    "reviewReasons": reasons,
                }
            )

        # 거래 원문 대신 전달 개수만 기록한다.
        logger.info(
            "OCR 입력 확인: page=%s, words=%s, rows=%s, chars=%s",
            number,
            len(checked_words),
            len(checked_rows),
            len(source),
        )

        result.append(
            {
                "pageNumber": number,
                "text": source,
                "words": checked_words,
                "rows": checked_rows,
            }
        )

    return result


def classify_expense_transactions(transactions: list[dict]) -> None:
    """거래 설명만 모델에 전달하고 같은 지출 거래에 예측 카테고리를 채운다."""
    targets = []
    descriptions = []
    for transaction in transactions:
        if transaction.get("transactionType") != "EXPENSE":
            continue
        description = str(transaction.get("description") or "").strip()
        if not description:
            transaction["category"] = "UNCLASSIFIED"
            continue
        targets.append(transaction)
        descriptions.append(description)

    if not targets:
        return
    model = getattr(app.state, "expense_model", None)
    if model is None:
        raise HTTPException(status_code=503, detail={
            "code": "EXPENSE_MODEL_UNAVAILABLE",
            "message": "지출 분류 모델이 준비되지 않았습니다.",
        })

    try:
        # 설명 배열과 원래 거래 참조를 같은 순서로 보관하므로 날짜·금액을 다시 조합하지 않는다.
        predictions = classify_descriptions(model["pipeline"], model["model_name"], descriptions)
        for transaction, prediction in zip(targets, predictions):
            transaction["category"] = prediction["category"]
    except Exception as exc:
        logger.exception("지출 카테고리 예측 실패")
        raise HTTPException(status_code=503, detail={
            "code": "EXPENSE_CLASSIFICATION_FAILED",
            "message": "지출 카테고리를 분류하지 못했습니다.",
        }) from exc


@app.post("/api/pdf/analyze")
def analyze_pdf(
    file: Annotated[UploadFile, File()],
    ocr_pages_json: Annotated[
        str | None,
        Form(alias="ocrPages"),
    ] = None,
) -> dict[str, Any]:
    try:
        data = file.file.read(
            MAX_FILE_BYTES + 1
        )

        if (
            not data
            or not data.startswith(b"%PDF-")
        ):
            raise input_error(
                "올바른 PDF 파일이 아닙니다."
            )

        if len(data) > MAX_FILE_BYTES:
            raise HTTPException(
                status_code=413,
                detail={
                    "code": "PDF_TOO_LARGE",
                    "message": "PDF 파일은 6MB 이하여야 합니다.",
                },
            )

        pages = parse_ocr_pages(
            ocr_pages_json
        )

        result = pdf_parser.extract_transactions(
            data,
            ocr_pages=pages,
        )

        if any(page["pageNumber"] > result["totalPages"] for page in pages):
            raise input_error(
                "OCR 페이지 번호가 PDF 페이지 수를 초과했습니다."
            )

        # 추출한 거래의 날짜·금액은 유지하고 지출 카테고리만 분류한다.
        classify_expense_transactions(result["transactions"])

        logger.info(
            "PDF 분석 완료: pages=%s, extracted=%s, parsed=%s, review=%s",
            result["totalPages"],
            result["extractedCount"],
            result["parsedCount"],
            result["needsReviewCount"],
        )

        return {
            "fileName": file.filename,
            **result,
        }

    except HTTPException:
        raise

    except ValueError as exc:
        messages = {
            "PDF_DAILY_DETAILS_REQUIRED": (
                "날짜별 상세 내역을 업로드해주세요. "
                "월별·지출처별 합계는 일별 거래로 저장하지 않습니다."
            ),
            "PDF_OCR_REQUIRED": (
                "이미지형 PDF의 OCR 결과가 전달되지 않았습니다. "
                "서버의 OCR 처리와 전달 결과를 확인해주세요."
            ),
            "PDF_TRANSACTIONS_NOT_FOUND": (
                "날짜별 거래 내역을 찾지 못했습니다. "
                "상세 내역 또는 OCR 인식 결과를 확인해주세요."
            ),
        }

        code = str(exc)

        if code in messages:
            raise HTTPException(
                status_code=422,
                detail={
                    "code": code,
                    "message": messages[code],
                },
            ) from exc

        raise input_error(
            "PDF 내용 또는 OCR 입력 형식을 확인해주세요."
        ) from exc

    except (PDFSyntaxError, PDFPasswordIncorrect, PdfminerException) as exc:
        raise input_error(
            "손상되었거나 분석할 수 없는 PDF 파일입니다."
        ) from exc

    except Exception as exc:
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
