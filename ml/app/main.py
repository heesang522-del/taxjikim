import json
import logging
import math

from contextlib import asynccontextmanager
from pathlib import Path
from threading import Event, Thread
from typing import Annotated

import joblib
from fastapi import FastAPI, File, Form, HTTPException, UploadFile
from pdfminer.pdfdocument import PDFPasswordIncorrect
from pdfminer.pdfparser import PDFSyntaxError
from pdfplumber.utils.exceptions import PdfminerException

from . import pdf_parser
from .expense_classifier import classify_descriptions

# 프로젝트 루트와 ml 폴더 중 어디서 서버를 실행해도 같은 스케줄러를 사용한다.
if __package__ == "ml.app":
    from ..scripts.retrain_scheduler import run_scheduler
else:
    from scripts.retrain_scheduler import run_scheduler


logger = logging.getLogger("uvicorn.error")
MODEL_DIR = Path(__file__).resolve().parents[1] / "models/expense_classifier"


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


# 서버 시작 시 모델과 스케줄러를 준비하고 종료 시 스케줄러를 정리한다.
@asynccontextmanager
async def lifespan(app: FastAPI):
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
    title="Taxjikim ML API",
    lifespan=lifespan,
)


def validation_error(message: str) -> HTTPException:
    """기존 PDF 입력 오류의 응답 형식을 만든다."""
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


def parse_ocr_pages(value: str | None) -> list[dict]:
    """Spring에서 전달한 페이지별 OCR 텍스트를 읽는다."""
    if value is None:
        return []

    try:
        pages = json.loads(value)

    except (json.JSONDecodeError, TypeError) as exception:
        raise validation_error(
            "ocrPages는 JSON 배열이어야 합니다."
        ) from exception

    if not isinstance(pages, list):
        raise validation_error(
            "ocrPages는 JSON 배열이어야 합니다."
        )

    seen = set()

    for page in pages:
        if not isinstance(page, dict):
            raise validation_error(
                "OCR 페이지 형식이 올바르지 않습니다."
            )

        number = page.get("pageNumber")

        if (
            type(number) is not int
            or number < 1
            or number in seen
            or not isinstance(page.get("text"), str)
            or type(page.get("ocrUsed")) is not bool
        ):
            raise validation_error(
                "OCR 페이지 번호와 텍스트를 확인해주세요."
            )

        words = page.get("words")

        if words is None:
            page["words"] = []
            words = []

        if not isinstance(words, list):
            raise validation_error(
                "OCR 단어 목록은 배열이어야 합니다."
            )

        for word in words:
            if not isinstance(word, dict):
                raise validation_error(
                    "OCR 단어 형식이 올바르지 않습니다."
                )

            if (
                not isinstance(word.get("text"), str)
                or not valid_coordinate(word.get("x0"))
                or not valid_coordinate(word.get("x1"))
                or not valid_coordinate(word.get("top"))
                or word["x1"] < word["x0"]
            ):
                raise validation_error(
                    "OCR 단어와 좌표를 확인해주세요."
                )

        seen.add(number)

    return pages


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


@app.get("/health")
def health() -> dict:
    """서버 상태 확인 요청에 응답한다."""
    return {"status": "ok"}


@app.post("/api/pdf/analyze")
def analyze_pdf(
    file: Annotated[UploadFile, File()],
    ocrPages: Annotated[str | None, Form()] = None,
) -> dict:
    """기존 PDF 거래 추출 결과에 지출 카테고리를 추가해 반환한다."""
    pages = parse_ocr_pages(ocrPages)
    data = file.file.read()

    if not data.startswith(b"%PDF-"):
        raise validation_error(
            "올바른 PDF 파일이 아닙니다."
        )

    try:
        result = pdf_parser.extract_transactions(
            data,
            ocr_pages=pages,
        )

        if any(
            page["pageNumber"] > result["totalPages"]
            for page in pages
        ):
            raise validation_error(
                "OCR 페이지 번호가 PDF 페이지 수를 초과했습니다."
            )

        # 수입·거래 구분·원문·금액은 유지하고 지출 거래의 category만 추가한다.
        classify_expense_transactions(result["transactions"])

        # 처리 건수를 먼저 출력한다.
        logger.info(
            "PDF 분석 결과: 추출 %s건, 성공 %s건, 실패 %s건",
            result["extractedCount"],
            result["parsedCount"],
            result["failedCount"],
        )

        # 거래별 금액·날짜·설명·원문을 포함한 분석 결과를 출력한다.
        if logger.isEnabledFor(logging.INFO):
            logger.info(
                "PDF 파싱 상세 결과:\n%s",
                json.dumps(
                    result,
                    ensure_ascii=False,
                    indent=2,
                ),
            )

        return {
            "fileName": file.filename,
            **result,
        }

    except (
        ValueError,
        PDFSyntaxError,
        PDFPasswordIncorrect,
        PdfminerException,
    ) as exception:
        raise validation_error(
            "손상되었거나 분석할 수 없는 PDF 파일입니다."
        ) from exception

    except HTTPException:
        raise

    except Exception as exception:
        logger.exception(
            "PDF 분석 중 오류가 발생했습니다."
        )

        raise HTTPException(
            status_code=500,
            detail={
                "code": "PDF_ANALYSIS_FAILED",
                "message": "PDF 분석에 실패했습니다.",
            },
        ) from exception
