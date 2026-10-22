import json
import logging

from contextlib import asynccontextmanager
from pathlib import Path
from typing import Annotated

from fastapi import FastAPI, File, Form, HTTPException, UploadFile
from pdfminer.pdfdocument import PDFPasswordIncorrect
from pdfminer.pdfparser import PDFSyntaxError
from pdfplumber.utils.exceptions import PdfminerException

from . import pdf_parser


logger = logging.getLogger("uvicorn.error")


@asynccontextmanager
async def lifespan(app: FastAPI):
    logger.info(
        "사용 중인 PDF 파서: %s",
        Path(pdf_parser.__file__).resolve(),
    )

    yield


app = FastAPI(
    title="Taxjikim ML API",
    lifespan=lifespan,
)


def validation_error(message: str) -> HTTPException:
    return HTTPException(
        status_code=400,
        detail={
            "code": "VALIDATION_ERROR",
            "message": message,
        },
    )


def parse_ocr_pages(value: str | None) -> list[dict]:
    if value is None:
        return []

    try:
        pages = json.loads(value)

    except (json.JSONDecodeError, TypeError) as exc:
        raise validation_error(
            "ocrPages는 JSON 배열이어야 합니다."
        ) from exc

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
                "OCR 페이지 번호와 텍스트 형식을 확인해주세요."
            )

        seen.add(number)

    return pages


@app.get("/health")
def health() -> dict:
    return {"status": "ok"}


@app.post("/api/pdf/analyze")
def analyze_pdf(
    file: Annotated[UploadFile, File()],
    ocrPages: Annotated[str | None, Form()] = None,
) -> dict:
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

        logger.info(
            "PDF 분석 결과: 추출 %s건, 성공 %s건, 실패 %s건",
            result["extractedCount"],
            result["parsedCount"],
            result["failedCount"],
        )

        # 개발 중 거래 설명·금액·원문·페이지별 결과를 확인한다.
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
    ) as exc:
        raise validation_error(
            "손상되었거나 분석할 수 없는 PDF 파일입니다."
        ) from exc

    except HTTPException:
        raise

    except Exception as exc:
        logger.exception(
            "PDF 분석 중 오류가 발생했습니다."
        )

        raise HTTPException(
            status_code=500,
            detail={
                "code": "PDF_ANALYSIS_FAILED",
                "message": "PDF 분석에 실패했습니다.",
            },
        ) from exc