from fastapi import FastAPI

app = FastAPI(title="Taxjikim ML API")


@app.get("/health")
def health():
    """모델 준비 여부와 별개로 API 서버의 실행 상태를 확인한다."""
    return {"status": "ok"}
