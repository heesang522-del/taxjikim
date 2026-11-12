"""정답이 있는 전체 수입 데이터로 LinearSVC를 학습한다. 시험 입력은 별도로 받는다."""

import argparse
import csv
import hashlib
import json
import tempfile
from datetime import datetime
from pathlib import Path
from zoneinfo import ZoneInfo

import joblib
import sklearn
from sklearn.feature_extraction.text import TfidfVectorizer
from sklearn.pipeline import Pipeline
from sklearn.svm import LinearSVC


if __package__:
    from .feedback_preprocessing import filter_feedback, normalized
else:
    from feedback_preprocessing import filter_feedback, normalized


BASE_DIR = Path(__file__).resolve().parents[1]
DATA_DIR = BASE_DIR / "data/income/training"
OUTPUT_DIR = BASE_DIR / "models/income_classifier"
INCOME_CATEGORIES = {"YOUTUBE_SETTLEMENT", "AD_SPONSORSHIP", "EXTERNAL_SUPPORT", "OTHER"}


# CSV 파일을 읽어 행 목록으로 반환한다.
def read_csv(path: Path) -> list[dict[str, str]]:
    with path.open(encoding="utf-8-sig", newline="") as file:
        return list(csv.DictReader(file))


# 행 목록을 CSV 파일로 저장한다.
def write_csv(path: Path, rows: list[dict[str, str]]) -> None:
    with path.open("w", encoding="utf-8-sig", newline="") as file:
        writer = csv.DictWriter(file, fieldnames=list(rows[0]))
        writer.writeheader()
        writer.writerows(rows)


# 학습 CSV를 읽고 설명과 정답의 누락을 확인한다.
def read_rows(path: Path) -> list[dict[str, str]]:
    rows = read_csv(path)
    if not rows or any(
        not row.get("description", "").strip()
        or not row.get("category_code", "").strip()
        for row in rows
    ):
        raise ValueError(f"수입 설명·정답 데이터를 먼저 추가해주세요: {path}")
    return rows


# 문자 전처리와 분류 모델을 하나로 묶는다.
def build_pipeline() -> Pipeline:
    return Pipeline([
        ("tfidf", TfidfVectorizer(analyzer="char", ngram_range=(2, 5), sublinear_tf=True)),
        ("classifier", LinearSVC(max_iter=5000, random_state=42)),
    ])


FIELDS = ("sample_id", "description", "category_code", "group_id", "review_status")


# 학습 자료를 합치고 중복과 정답 충돌을 확인한다.
def merge_training_rows(
    train: list[dict[str, str]],
    additions: list[dict[str, str]],
) -> tuple[list[dict[str, str]], dict[str, int]]:
    labels = {row["category_code"] for row in train}
    seen: dict[str, dict[str, str]] = {
        field: {} for field in ("sample_id", "group_id", "description")
    }
    merged = []
    counts = {"added": 0, "duplicates": 0}
    for index, original in enumerate(train + additions):
        row = {field: (original.get(field) or "").strip() for field in FIELDS}
        if any(not row[field] for field in FIELDS):
            raise ValueError("CSV 필수값이 비어 있습니다: " + str(index + 1))
        is_addition = index >= len(train)
        if is_addition and row["review_status"] != "APPROVED":
            raise ValueError("추가 CSV는 사람이 검토한 APPROVED 행만 허용합니다.")
        label = row["category_code"]
        if label not in labels:
            raise ValueError(f"지원하지 않는 수입 카테고리: {label}")
        keys = {field: normalized(row[field]) for field in seen}
        for field, key in keys.items():
            if key in seen[field] and seen[field][key] != label:
                raise ValueError(f"같은 {field}에 서로 다른 정답이 있습니다.")
        # 같은 ID의 다른 설명은 갱신으로 간주하지 않고 원본 확인을 요구한다.
        if keys["sample_id"] in seen["sample_id"]:
            matching = next(item for item in merged if normalized(item["sample_id"]) == keys["sample_id"])
            if normalized(matching["description"]) != keys["description"] or normalized(matching["group_id"]) != keys["group_id"]:
                raise ValueError("같은 sample_id의 내용이 변경되었습니다.")
        duplicate = keys["description"] in seen["description"] or keys["sample_id"] in seen["sample_id"]
        for field, key in keys.items():
            seen[field][key] = label
        if duplicate:
            counts["duplicates"] += 1
            continue
        merged.append(row)
        if is_addition:
            counts["added"] += 1
    return merged, counts


# 명령 옵션에 맞게 학습 자료를 읽고 모델을 학습한다.
def main() -> None:
    # 터미널에서 실행할 때 사용할 옵션을 설정한다.
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--overwrite", action="store_true", help="기존 모델 덮어쓰기")
    parser.add_argument("--data-dir", type=Path, default=DATA_DIR)
    parser.add_argument("--output-dir", type=Path)
    parser.add_argument("--incoming-dir", type=Path,
                        default=BASE_DIR / "data/income/user_approved",
                        help="추가 CSV 폴더. 없거나 비어 있으면 기본 자료만 학습")
    args = parser.parse_args()
    args.output_dir = args.output_dir or OUTPUT_DIR
    if args.output_dir.exists() and not args.overwrite:
        parser.error("결과 폴더가 이미 있습니다. 다시 학습하려면 --overwrite를 지정하세요.")

    source = args.data_dir / "training_data.csv"
    print(f"학습 CSV: {source}", flush=True)
    sources = [source]
    incoming = sorted(args.incoming_dir.glob("*.csv"))
    paths = sources + incoming
    contents = {path: path.read_bytes() for path in paths}
    base_rows = [row for path in sources for row in read_rows(path)]
    additions = []
    for path in incoming:
        additions.extend(read_csv(path))
    additions, excluded = filter_feedback(base_rows, additions)
    rows, counts = merge_training_rows(base_rows, additions)
    counts["duplicates"] += sum(row["reason"] == "중복" for row in excluded)
    if any(path.read_bytes() != data for path, data in contents.items()):
        raise ValueError("읽는 중 학습 CSV가 변경됐습니다. 다시 실행해주세요.")
    if excluded:
        print(f"추가 학습 후보 제외 {len(excluded)}건")

    descriptions = [row["description"] for row in rows]
    categories = [row["category_code"] for row in rows]
    labels = sorted(set(categories))
    if len(labels) < 2 or not set(labels).issubset(INCOME_CATEGORIES):
        raise ValueError("학습 정답 카테고리를 확인해주세요. 미분류는 정답에 넣지 않습니다.")

    report = {
        "created_at": datetime.now(ZoneInfo("Asia/Seoul")).isoformat(),
        "sklearn_version": sklearn.__version__,
        "train_count": len(rows), "model_input": ["description"], "labels": labels,
        "source_sha256": hashlib.sha256(contents[source]).hexdigest(),
        "source_file": str(source.resolve()),
        "input_sha256": {str(path.resolve()): hashlib.sha256(data).hexdigest()
                         for path, data in contents.items()},
        "base_count": len(base_rows), "additional_count": counts["added"],
        "duplicate_count": counts["duplicates"],
        "dataset_review_status": sorted({row.get("review_status", "UNKNOWN") for row in rows}),
        "training_mode": "all_labeled_rows",
        "evaluation": "not_evaluated_use_separate_manual_inputs",
        "selection_rule": "fixed_linear_svc",
        "selected_model": "linear_svc",
    }
    print(f"기본 {len(base_rows)}건 + 추가 {counts['added']}건 / 중복 제외 {counts['duplicates']}건")
    args.output_dir.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(prefix="income-training-", dir=args.output_dir.parent) as temp:
        staging_dir = Path(temp)
        pipeline = build_pipeline()
        pipeline.fit(descriptions, categories)
        model_path = staging_dir / "linear_svc_bundle.pkl"
        joblib.dump({
            "pipeline": pipeline, "model_name": "linear_svc",
            "transaction_type": "INCOME", "input_fields": ["description"],
            "labels": labels, "sklearn_version": sklearn.__version__,
            "dataset_review_status": report["dataset_review_status"],
        }, model_path)
        restored = joblib.load(model_path)["pipeline"]
        if list(restored.predict(descriptions)) != list(pipeline.predict(descriptions)):
            raise ValueError("모델 저장 전후 예측 결과가 다릅니다.")
        # 학습 자료를 다시 맞힌 점수를 시험 정확도처럼 기록하지 않는다.
        report_path = staging_dir / "model_report.json"
        report_path.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
        # 학습과 저장 검증이 끝난 결과만 서비스 또는 재학습 폴더에 반영한다.
        args.output_dir.mkdir(parents=True, exist_ok=True)
        model_path.replace(args.output_dir / model_path.name)
        report_path.replace(args.output_dir / report_path.name)
    print(f"LinearSVC: 전체 {len(rows)}건 학습·저장 완료", flush=True)


if __name__ == "__main__":
    try:
        main()
    except (ValueError, OSError) as exc:
        raise SystemExit(f"학습 중단: {exc}") from exc
