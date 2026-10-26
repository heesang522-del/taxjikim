"""정답이 있는 전체 지출 데이터로 두 모델을 학습한다. 시험 입력은 별도로 받는다."""

import argparse
import csv
import hashlib
import json
from datetime import datetime
from pathlib import Path
from zoneinfo import ZoneInfo

import joblib
import sklearn
from sklearn.feature_extraction.text import TfidfVectorizer
from sklearn.linear_model import LogisticRegression
from sklearn.pipeline import Pipeline
from sklearn.svm import LinearSVC


BASE_DIR = Path(__file__).resolve().parents[1]
DATA_DIR = BASE_DIR / "data/expense/training"
OUTPUT_DIR = BASE_DIR / "models/expense_classifier"


def read_csv(path: Path) -> list[dict[str, str]]:
    """UTF-8 CSV를 행 목록으로 읽는다."""
    with path.open(encoding="utf-8-sig", newline="") as file:
        return list(csv.DictReader(file))


def write_csv(path: Path, rows: list[dict[str, str]]) -> None:
    """설명·정답 자료를 원래 열 그대로 CSV에 저장한다."""
    with path.open("w", encoding="utf-8-sig", newline="") as file:
        writer = csv.DictWriter(file, fieldnames=list(rows[0]))
        writer.writeheader()
        writer.writerows(rows)


def read_rows(path: Path) -> list[dict[str, str]]:
    """설명과 정답 카테고리가 있는 학습 자료를 읽는다."""
    rows = read_csv(path)
    if not rows or any(
        not row.get("description", "").strip()
        or not row.get("category_code", "").strip()
        for row in rows
    ):
        raise ValueError(f"설명·정답을 확인해주세요: {path.name}")
    return rows


def build_pipeline(classifier: LogisticRegression | LinearSVC) -> Pipeline:
    """설명 전처리와 분류 모델을 학습·예측에서 함께 사용한다."""
    return Pipeline([
        ("tfidf", TfidfVectorizer(analyzer="char", ngram_range=(2, 5), sublinear_tf=True)),
        ("classifier", classifier),
    ])


def main() -> None:
    """전체 정답 데이터를 학습하고 두 모델과 서버 선택 정보를 저장한다."""
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--overwrite", action="store_true", help="기존 모델 덮어쓰기")
    parser.add_argument("--data-dir", type=Path, default=DATA_DIR)
    parser.add_argument("--output-dir", type=Path, default=OUTPUT_DIR)
    parser.add_argument("--selected-model", choices=("logistic_regression", "linear_svc"),
                        default="logistic_regression", help="서버에서 사용할 모델 (자동 비교 선택 아님)")
    args = parser.parse_args()
    if args.output_dir.exists() and not args.overwrite:
        parser.error("결과 폴더가 이미 있습니다. 다시 학습하려면 --overwrite를 지정하세요.")

    source = args.data_dir / "training_data.csv"
    rows = read_rows(source)
    descriptions = [row["description"] for row in rows]
    categories = [row["category_code"] for row in rows]
    labels = sorted(set(categories))
    if len(labels) < 2 or "UNCLASSIFIED" in labels:
        raise ValueError("학습 정답 카테고리를 확인해주세요. 미분류는 정답에 넣지 않습니다.")

    report = {
        "created_at": datetime.now(ZoneInfo("Asia/Seoul")).isoformat(),
        "sklearn_version": sklearn.__version__,
        "train_count": len(rows), "model_input": ["description"], "labels": labels,
        "source_sha256": hashlib.sha256(source.read_bytes()).hexdigest(),
        "dataset_review_status": sorted({row.get("review_status", "UNKNOWN") for row in rows}),
        "training_mode": "all_labeled_rows",
        "evaluation": "not_evaluated_use_separate_manual_inputs",
        "selection_rule": "explicit_selection_not_test_score",
        "selected_model": args.selected_model,
    }
    candidates = {
        "logistic_regression": LogisticRegression(max_iter=2000, random_state=42),
        "linear_svc": LinearSVC(max_iter=5000, random_state=42),
    }
    args.output_dir.mkdir(parents=True, exist_ok=True)
    for name, classifier in candidates.items():
        pipeline = build_pipeline(classifier)
        pipeline.fit(descriptions, categories)
        path = args.output_dir / f"{name}_bundle.pkl"
        joblib.dump({
            "pipeline": pipeline, "model_name": name,
            "transaction_type": "EXPENSE", "input_fields": ["description"],
            "labels": labels, "sklearn_version": sklearn.__version__,
            "dataset_review_status": report["dataset_review_status"],
        }, path)
        restored = joblib.load(path)["pipeline"]
        if list(restored.predict(descriptions)) != list(pipeline.predict(descriptions)):
            raise ValueError("모델 저장 전후 예측 결과가 다릅니다.")
        print(f"{name}: 전체 {len(rows)}건 학습·저장 완료", flush=True)

    # 학습 자료를 다시 맞힌 점수를 시험 정확도처럼 기록하지 않는다.
    (args.output_dir / "model_report.json").write_text(
        json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8",
    )
    print(f"서버 선택 모델: {args.selected_model} (새 시험 점수로 선택한 결과 아님)")


if __name__ == "__main__":
    main()
