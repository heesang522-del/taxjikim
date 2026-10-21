"""지출 설명 분류의 두 후보를 학습하고 검증 자료로 비교한다."""

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
DATA_DIR = BASE_DIR / "data/expense/category_dummy_v3"
OUTPUT_DIR = BASE_DIR / "models/expense_baseline_v2"


def read_rows(path: Path) -> list[dict[str, str]]:
    """설명과 정답이 있는지 확인한다. PENDING 자료는 초기 실험용으로만 사용한다."""
    with path.open(encoding="utf-8-sig", newline="") as file:
        rows = list(csv.DictReader(file))
    if not rows or any(
        not row.get("description", "").strip()
        or not row.get("category_code", "").strip()
        for row in rows
    ):
        raise ValueError(f"빈 자료 또는 필수값 누락: {path.name}")
    return rows


def build_pipeline(classifier: LogisticRegression | LinearSVC) -> Pipeline:
    """문자 조각을 숫자로 바꾸는 전처리와 분류기를 한 묶음으로 구성한다."""
    return Pipeline([
        # 한국어 표현을 2~5글자 조각으로 나눈다. 소문자 변환·연속 공백 정리는
        # TfidfVectorizer의 문자 분석기가 처리한다. 단어 의미를 이해하는 모델은 아니다.
        ("tfidf", TfidfVectorizer(analyzer="char", ngram_range=(2, 5), sublinear_tf=True)),
        ("classifier", classifier),
    ])


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--overwrite", action="store_true", help="기존 실험 결과 덮어쓰기")
    args = parser.parse_args()
    if OUTPUT_DIR.exists() and not args.overwrite:
        parser.error("실험 결과가 이미 있습니다. 다시 실행하려면 --overwrite를 지정하세요.")

    print("학습·검증 자료를 확인합니다.", flush=True)
    source = DATA_DIR / "labeled_candidates.csv"
    split_dir = DATA_DIR / "splits"
    split_meta = json.loads((split_dir / "split_manifest.json").read_text(encoding="utf-8"))
    if hashlib.sha256(source.read_bytes()).hexdigest() != split_meta["source_sha256"]:
        raise ValueError("원본 데이터가 분할 이후 바뀌었습니다. 분할부터 다시 확인하세요.")
    train = read_rows(split_dir / "train.csv")
    validation = read_rows(split_dir / "validation.csv")
    # 검증 자료를 보고 모델을 조정하므로 test.csv는 이 파일에서 읽지 않는다.
    for field in ("sample_id", "group_id", "semantic_group_id", "description"):
        if {row[field] for row in train} & {row[field] for row in validation}:
            raise ValueError(f"학습·검증 사이에 겹치는 {field}가 있습니다.")

    x_train = [row["description"] for row in train]
    y_train = [row["category_code"] for row in train]
    x_validation = [row["description"] for row in validation]
    y_validation = [row["category_code"] for row in validation]
    labels = sorted(set(y_train))
    if len(labels) < 2 or set(y_validation) != set(labels):
        raise ValueError("학습·검증 카테고리 구성을 확인하세요.")

    candidates = {
        "logistic_regression": LogisticRegression(max_iter=2000, random_state=42),
        "linear_svc": LinearSVC(max_iter=5000, random_state=42),
    }
    OUTPUT_DIR.mkdir(parents=True, exist_ok=True)
    report = {
        "experiment": "expense_baseline_v2",
        "created_at": datetime.now(ZoneInfo("Asia/Seoul")).isoformat(),
        "sklearn_version": sklearn.__version__,
        "train_count": len(train),
        "validation_count": len(validation),
        "model_input": ["description"],
        "labels": labels,
        "data_hashes": {
            name: hashlib.sha256((split_dir / name).read_bytes()).hexdigest()
            for name in ("train.csv", "validation.csv", "split_manifest.json")
        },
        "test_evaluated": False,
        "abstention_enabled": False,
        "status": "EXPERIMENT_ONLY_NOT_FOR_SERVING",
        "candidates": {},
    }
    for name, classifier in candidates.items():
        print(f"{name} 학습 중...", flush=True)
        pipeline = build_pipeline(classifier)
        # fit은 학습 자료에만 적용한다. 검증에서는 저장된 TF-IDF 기준을 재사용한다.
        pipeline.fit(x_train, y_train)
        predicted = pipeline.predict(x_validation)
        metrics = {
            "accuracy": float(accuracy_score(y_validation, predicted)),
            "macro_f1": float(f1_score(y_validation, predicted, average="macro", zero_division=0)),
            "classification_report": classification_report(
                y_validation, predicted, labels=labels, output_dict=True, zero_division=0,
            ),
            "confusion_matrix": confusion_matrix(y_validation, predicted, labels=labels).tolist(),
        }
        report["candidates"][name] = metrics

        # 전처리와 모델을 함께 저장한다. 아직 미분류 처리나 API에 연결하지 않는다.
        bundle_path = OUTPUT_DIR / f"{name}_bundle.pkl"
        joblib.dump({
            "pipeline": pipeline,
            "model_name": name,
            "transaction_type": "EXPENSE",
            "input_fields": ["description"],
            "labels": labels,
            "sklearn_version": sklearn.__version__,
            "status": "EXPERIMENT_ONLY_NOT_FOR_SERVING",
        }, bundle_path)
        restored = joblib.load(bundle_path)
        if list(restored["pipeline"].predict(x_validation)) != list(predicted):
            raise ValueError("저장 전후 예측 결과가 다릅니다.")

        with (OUTPUT_DIR / f"{name}_validation.csv").open(
            "w", encoding="utf-8-sig", newline="",
        ) as file:
            writer = csv.DictWriter(file, fieldnames=[
                "sample_id", "description", "expected_category", "predicted_category", "correct",
            ])
            writer.writeheader()
            for row, prediction in zip(validation, predicted):
                writer.writerow({
                    "sample_id": row["sample_id"], "description": row["description"],
                    "expected_category": row["category_code"], "predicted_category": prediction,
                    "correct": row["category_code"] == prediction,
                })
        print(f"  검증 정확도 {metrics['python ml/scripts/train_expense.py --overwrite']:.3f} / macro F1 {metrics['macro_f1']:.3f}", flush=True)

    (OUTPUT_DIR / "validation_report.json").write_text(
        json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8",
    )
    print("저장·재로딩 예측 일치 확인 완료. 최종 시험·미분류 기준·API 연결은 아직입니다.")


if __name__ == "__main__":
    main()
