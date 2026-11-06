"""Spring OCR 결과를 받아 날짜별 거래를 분석한다."""

from __future__ import annotations

import csv
import json
import logging
import math

from collections.abc import AsyncIterator, Callable
from contextlib import asynccontextmanager
from pathlib import Path
from threading import Event, Lock, Thread
from typing import Literal

from pydantic import BaseModel, Field
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
from .expense_classifier import classify_descriptions as classify_expense_descriptions
from .income_classifier import classify_descriptions as classify_income_descriptions

# 프로젝트 루트와 ml 폴더 중 어디서 서버를 실행해도 같은 스케줄러를 사용한다.
if __package__ == "ml.app":
    from ..scripts.feedback_preprocessing import filter_feedback, write_feedback_review
    from ..scripts.retrain_scheduler import read_last_training, run_scheduler
else:
    from scripts.feedback_preprocessing import filter_feedback, write_feedback_review
    from scripts.retrain_scheduler import read_last_training, run_scheduler


logger = logging.getLogger("uvicorn.error")

MAX_FILE_BYTES = 6 * 1024 * 1024
MAX_PAGES = 50

MODEL_DIR = Path(__file__).resolve().parent.parent / "models"


# 학습 보고서에서 선택된 거래 분류 모델을 읽는다.
def load_transaction_model(kind: str, label: str) -> dict:
    model_dir = MODEL_DIR / f"{kind}_classifier"
    try:
        report = json.loads(
            (model_dir / "model_report.json").read_text(
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
            model_dir / f"{selected}_bundle.pkl"
        )

    except (
        FileNotFoundError,
        KeyError,
        ValueError,
    ) as exc:
        raise RuntimeError(
            f"{label} 모델을 먼저 준비해주세요: "
            f"python scripts/train_{kind}.py"
        ) from exc


# 성공 기록과 학습 보고서가 일치할 때만 기존 모델 종류의 새 번들을 적용한다.
def apply_retrained_model(app: FastAPI, kind: str) -> None:
    retraining_dir = MODEL_DIR / "retraining"
    classifier_dir = retraining_dir / kind / "classifier"
    try:
        record = read_last_training(retraining_dir / f"{kind}_last_training.json")
        trained_at = record.get("trained_at")
        if not trained_at or trained_at == getattr(app.state, f"{kind}_trained_at", None):
            return

        report = json.loads((classifier_dir / "model_report.json").read_text(encoding="utf-8"))
        versions = {
            str(Path(path).relative_to(MODEL_DIR.parent)): digest
            for path, digest in report["input_sha256"].items()
        }
        if versions != record["data_versions"]:
            logger.warning("재학습 성공 기록과 모델 자료가 일치하지 않습니다: %s", kind)
            return

        current_model = getattr(app.state, f"{kind}_model")
        selected = current_model["model_name"]
        bundle = joblib.load(classifier_dir / f"{selected}_bundle.pkl")
        if bundle["model_name"] != selected or not bundle["pipeline"]:
            raise ValueError("분류 모델 번들 형식을 확인해주세요.")
    except Exception:
        logger.exception("재학습 모델 적용 실패. 기존 모델 사용: %s", kind)
        return

    setattr(app.state, f"{kind}_model", bundle)
    setattr(app.state, f"{kind}_trained_at", trained_at)
    logger.info("재학습 모델 적용 완료: 유형=%s, 모델=%s, 학습=%s", kind, selected, trained_at)


# 서버 시작 시 모델과 스케줄러를 준비하고 종료 시 스케줄러를 정리한다.
@asynccontextmanager
async def lifespan(app: FastAPI) -> AsyncIterator[None]:
    logger.info(
        "사용 중인 PDF 파서: %s",
        Path(pdf_parser.__file__).resolve(),
    )
    app.state.expense_model = load_transaction_model("expense", "지출")
    logger.info("지출 분류 모델: %s", app.state.expense_model["model_name"])

    app.state.income_model = load_transaction_model("income", "수입")
    logger.info("수입 분류 모델: %s", app.state.income_model["model_name"])
    for kind in ("income", "expense"):
        setattr(app.state, f"{kind}_trained_at", None)
        apply_retrained_model(app, kind)

    # 요청을 처리하는 서버와 별도로 스케줄러를 실행한다.
    stop_event = Event()
    worker = Thread(target=run_scheduler, args=(stop_event, lambda kind: apply_retrained_model(app, kind)))
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


# 같은 유형의 거래 설명을 모델에 전달하고 원래 거래에 카테고리를 채운다.
def classify_transactions(
    transactions: list[dict],
    kind: str,
    label: str,
    classify_descriptions: Callable,
) -> None:
    transaction_type = kind.upper()
    targets = []
    descriptions = []
    for transaction in transactions:
        if transaction.get("transactionType") != transaction_type:
            continue
        description = str(transaction.get("description") or "").strip()
        if not description:
            transaction["category"] = "UNCLASSIFIED"
            continue
        targets.append(transaction)
        descriptions.append(description)

    if not targets:
        return
    model = getattr(app.state, f"{kind}_model", None)
    if model is None:
        raise HTTPException(status_code=503, detail={
            "code": f"{transaction_type}_MODEL_UNAVAILABLE",
            "message": f"{label} 분류 모델이 준비되지 않았습니다.",
        })

    try:
        # 설명 배열과 원래 거래 참조를 같은 순서로 보관하므로 날짜·금액을 다시 조합하지 않는다.
        predictions = classify_descriptions(model["pipeline"], model["model_name"], descriptions)
        for transaction, prediction in zip(targets, predictions):
            transaction["category"] = prediction["category"]
    except Exception as exc:
        logger.exception("%s 카테고리 예측 실패", label)
        raise HTTPException(status_code=503, detail={
            "code": f"{transaction_type}_CLASSIFICATION_FAILED",
            "message": f"{label} 카테고리를 분류하지 못했습니다.",
        }) from exc


class TrainingFeedback(BaseModel):
    transactionId: int = Field(gt=0)
    transactionType: Literal["INCOME", "EXPENSE"]
    description: str = Field(min_length=1, max_length=255)
    category: str = Field(pattern=r"^[A-Z][A-Z0-9_]{0,49}$")


FEEDBACK_LOCK = Lock()
FEEDBACK_FIELDS = ("sample_id", "description", "category_code", "group_id", "review_status")


# 같은 거래의 최종 카테고리를 추가 학습 CSV에 반영한다.
@app.post("/api/training-feedback", status_code=204)
def save_training_feedback(feedback: list[TrainingFeedback]) -> None:
    data_dir = MODEL_DIR.parent / "data"
    with FEEDBACK_LOCK:
        for kind in ("income", "expense"):
            items = [item for item in feedback if item.transactionType.lower() == kind]
            if not items:
                continue
            target = data_dir / kind / "user_approved" / "category_feedback.csv"
            base = data_dir / kind / "training" / "training_data.csv"
            with base.open(encoding="utf-8-sig", newline="") as file:
                base_rows = list(csv.DictReader(file))
            labels = {row["category_code"] for row in base_rows}
            rows = {}
            if target.exists():
                with target.open(encoding="utf-8-sig", newline="") as file:
                    rows = {row["sample_id"]: row for row in csv.DictReader(file)}
            for item in items:
                sample_id = f"user-category-{item.transactionId}"
                description = item.description.strip()
                if not description or item.category not in labels or item.category == "UNCLASSIFIED":
                    rows.pop(sample_id, None)
                    continue
                rows[sample_id] = dict(zip(
                    FEEDBACK_FIELDS, (sample_id, description, item.category, sample_id, "APPROVED")
                ))
            # 통과한 후보만 학습용 파일에 저장하고 제외 기록은 따로 보관한다.
            accepted, excluded = filter_feedback(base_rows, list(rows.values()))
            review_path = target.parent.parent / "user_approved_review.csv"
            review = {}
            if review_path.exists():
                with review_path.open(encoding="utf-8-sig", newline="") as file:
                    review = {row["sample_id"]: row for row in csv.DictReader(file)}
            for item in items:
                review.pop(f"user-category-{item.transactionId}", None)
            for row in accepted:
                review.pop(row["sample_id"], None)
            review.update((row["sample_id"], row) for row in excluded)
            rows = {row["sample_id"]: row for row in accepted}

            target.parent.mkdir(parents=True, exist_ok=True)
            temporary = target.with_suffix(".tmp")
            with temporary.open("w", encoding="utf-8", newline="") as file:
                writer = csv.DictWriter(file, fieldnames=FEEDBACK_FIELDS)
                writer.writeheader()
                writer.writerows(rows.values())
            write_feedback_review(target.parent, list(review.values()))
            temporary.replace(target)
            saved_items = [
                (item.transactionId, item.description.strip(), item.category)
                for item in items
                if f"user-category-{item.transactionId}" in rows
            ]
            logger.info("재학습 CSV 저장 완료: 유형=%s, 저장 내역(거래번호, 설명, 카테고리)=%r", kind, saved_items)


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

        # 1. PDF 원본과 OCR 결과로 거래 목록을 만든다.
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

        # 2. 지출·수입을 각각 분류해 원래 거래에 카테고리를 붙인다.
        transactions = result["transactions"]
        classify_transactions(transactions, "expense", "지출", classify_expense_descriptions)
        classify_transactions(transactions, "income", "수입", classify_income_descriptions)

        logger.info(
            "PDF 분석 완료: pages=%s, extracted=%s, parsed=%s, review=%s",
            result["totalPages"],
            result["extractedCount"],
            result["parsedCount"],
            result["needsReviewCount"],
        )

        # 3. 카테고리가 붙은 거래 목록과 파일명을 Java에 돌려준다.
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
