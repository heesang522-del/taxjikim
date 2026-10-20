from fastapi import FastAPI, File, HTTPException, UploadFile
from .pdf_parser import extract_transactions

app = FastAPI(title="TaxJiKim PDF API")


@app.get("/health")
def health():
    """모델 준비 여부와 별개로 API 서버의 실행 상태를 확인한다."""
    return {"status": "ok"}


@app.post("/api/pdf/analyze")
async def analyze_pdf(file: UploadFile = File(...)):

    data = await file.read()

    if not data.startswith(b"%PDF-"):
        raise HTTPException(
            status_code=400,
            detail="올바른 PDF 파일이 아닙니다."
        )

    try:
        return {
            "fileName": file.filename,
            **extract_transactions(data)
        }

    except Exception as exc:
        raise HTTPException(
            status_code=422,
            detail="PDF 분석에 실패했습니다."
        ) from exc
