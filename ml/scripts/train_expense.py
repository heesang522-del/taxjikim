"""지출 분류 모델 두 개를 같은 train/test로 비교하고 PDF 분류용 모델을 저장한다."""

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
from sklearn.metrics import accuracy_score, classification_report, confusion_matrix, f1_score
from sklearn.pipeline import Pipeline
from sklearn.svm import LinearSVC


BASE_DIR = Path(__file__).resolve().parents[1]
DATA_DIR = BASE_DIR / "data/expense/category_dummy_v4_draft"
OUTPUT_DIR = BASE_DIR / "models/expense_classifier"


def read_rows(path: Path) -> list[dict[str, str]]:
    """분할된 지출 설명·정답 자료를 읽는다."""
    with path.open(encoding="utf-8-sig", newline="") as file:
        rows = list(csv.DictReader(file))
    if not rows or any(
        not row.get("description", "").strip() or not row.get("category_code", "").strip()
        for row in rows
    ):
        raise ValueError(f"설명·정답을 확인해주세요: {path.name}")
    return rows


def build_pipeline(classifier: LogisticRegression | LinearSVC) -> Pipeline:
    """기존 TF-IDF 전처리와 분류기를 함께 학습·저장하는 파이프라인을 만든다."""
    return Pipeline([
        # 전처리는 학습 설명으로만 학습하며 API에서도 같은 파이프라인을 사용한다.
        ("tfidf", TfidfVectorizer(analyzer="char", ngram_range=(2, 5), sublinear_tf=True)),
        ("classifier", classifier),
    ])


def main() -> None:
    """두 모델을 학습·시험하고 비교 결과와 사용할 모델 이름을 저장한다."""
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--overwrite", action="store_true", help="기존 학습 결과 덮어쓰기")
    args = parser.parse_args()
    if OUTPUT_DIR.exists() and not args.overwrite:
        parser.error("학습 결과가 이미 있습니다. 다시 실행하려면 --overwrite를 지정하세요.")

    split_dir = DATA_DIR / "train_test"
    split_meta = json.loads((split_dir / "split_manifest.json").read_text(encoding="utf-8"))
    source_hash = hashlib.sha256((DATA_DIR / "labeled_candidates.csv").read_bytes()).hexdigest()
    if source_hash != split_meta["source_sha256"]:
        raise ValueError("분할 후 원본 자료가 바뀌었습니다. 분할부터 다시 실행해주세요.")
    train = read_rows(split_dir / "train.csv")
    test = read_rows(split_dir / "test.csv")
    for field in ("sample_id", "group_id", "description"):
        if {row[field] for row in train} & {row[field] for row in test}:
            raise ValueError(f"학습·시험 사이에 겹치는 {field}가 있습니다.")

    x_train = [row["description"] for row in train]
    y_train = [row["category_code"] for row in train]
    x_test = [row["description"] for row in test]
    y_test = [row["category_code"] for row in test]
    labels = sorted(set(y_train))
    if len(labels) < 2 or set(y_test) != set(labels):
        raise ValueError("학습·시험 카테고리 구성을 확인해주세요.")

    candidates = {
        "logistic_regression": LogisticRegression(max_iter=2000, random_state=42),
        "linear_svc": LinearSVC(max_iter=5000, random_state=42),
    }
    report = {
        "created_at": datetime.now(ZoneInfo("Asia/Seoul")).isoformat(),
        "sklearn_version": sklearn.__version__,
        "train_count": len(train),
        "test_count": len(test),
        "model_input": ["description"],
        "labels": labels,
        "dataset_review_status": "PENDING",
        "data_hashes": {
            name: hashlib.sha256((split_dir / name).read_bytes()).hexdigest()
            for name in ("train.csv", "test.csv", "split_manifest.json")
        },
        "selection_rule": "macro_f1_then_accuracy_on_same_test",
        "limitation": "합성 후보의 같은 시험 자료로 비교·선택한 결과이며 별도의 독립 최종 평가 점수가 아님",
        "candidates": {},
    }
    predictions = {}
    OUTPUT_DIR.mkdir(parents=True, exist_ok=True)
    print(f"학습 {len(train)}건 / 시험 {len(test)}건. 별도 validation은 사용하지 않습니다.", flush=True)
    for name, classifier in candidates.items():
        print(f"{name} 학습 중...", flush=True)
        pipeline = build_pipeline(classifier)
        pipeline.fit(x_train, y_train)
        predicted = pipeline.predict(x_test)
        predictions[name] = list(predicted)
        metrics = {
            "accuracy": float(accuracy_score(y_test, predicted)),
            "macro_f1": float(f1_score(y_test, predicted, average="macro", zero_division=0)),
            "classification_report": classification_report(
                y_test, predicted, labels=labels, output_dict=True, zero_division=0,
            ),
            "confusion_matrix": confusion_matrix(y_test, predicted, labels=labels).tolist(),
        }
        report["candidates"][name] = metrics
        bundle_path = OUTPUT_DIR / f"{name}_bundle.pkl"
        joblib.dump({
            "pipeline": pipeline,
            "model_name": name,
            "transaction_type": "EXPENSE",
            "input_fields": ["description"],
            "labels": labels,
            "sklearn_version": sklearn.__version__,
            "dataset_review_status": "PENDING",
        }, bundle_path)
        restored = joblib.load(bundle_path)
        if list(restored["pipeline"].predict(x_test)) != list(predicted):
            raise ValueError("저장 전후 예측 결과가 다릅니다.")
        print(f"  시험 정확도 {metrics['accuracy']:.3f} / macro F1 {metrics['macro_f1']:.3f}", flush=True)

    # 비교 기준은 실행 전에 고정한다. 시험 점수를 보고 학습 설정을 반복 조정하지 않는다.
    selected = max(candidates, key=lambda name: (
        report["candidates"][name]["macro_f1"], report["candidates"][name]["accuracy"],
    ))
    report["selected_model"] = selected
    with (OUTPUT_DIR / "test_predictions.csv").open("w", encoding="utf-8-sig", newline="") as file:
        writer = csv.DictWriter(file, fieldnames=[
            "sample_id", "description", "expected_category", "logistic_regression", "linear_svc",
        ])
        writer.writeheader()
        for index, row in enumerate(test):
            writer.writerow({
                "sample_id": row["sample_id"], "description": row["description"],
                "expected_category": row["category_code"],
                **{name: values[index] for name, values in predictions.items()},
            })
    # 보고서는 모델 두 개의 저장이 끝난 뒤 작성하고 API가 선택 모델을 확인할 때 사용한다.
    (OUTPUT_DIR / "test_report.json").write_text(
        json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8",
    )
    print(f"PDF 분류에 사용할 모델: {selected}")
    print(f"저장 위치: {OUTPUT_DIR}")


if __name__ == "__main__":
    main()
