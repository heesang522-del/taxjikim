"""정답이 있는 전체 수입 데이터로 두 모델을 학습한다. 시험 입력은 별도로 받는다."""

import argparse
import csv
import hashlib
import json
import os
import shutil
import tempfile
import unicodedata
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
DATA_DIR = BASE_DIR / "data/income/training"
OUTPUT_DIR = BASE_DIR / "models/income_classifier"
SETFIT_DIR = BASE_DIR / "models/income_setfit"
INCOME_CATEGORIES = {"YOUTUBE_SETTLEMENT", "AD_SPONSORSHIP", "EXTERNAL_SUPPORT", "OTHER"}
SETFIT_BASE_MODEL = "sentence-transformers/paraphrase-multilingual-MiniLM-L12-v2"


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
        raise ValueError(f"수입 설명·정답 데이터를 먼저 추가해주세요: {path}")
    return rows


def build_pipeline(classifier: LogisticRegression | LinearSVC) -> Pipeline:
    """설명 전처리와 분류 모델을 학습·예측에서 함께 사용한다."""
    return Pipeline([
        ("tfidf", TfidfVectorizer(analyzer="char", ngram_range=(2, 5), sublinear_tf=True)),
        ("classifier", classifier),
    ])


FIELDS = ("sample_id", "description", "category_code", "group_id", "review_status")


def normalized(text: str) -> str:
    """공백·대소문자·유니코드 표현 차이로 중복이 누락되지 않게 한다."""
    return " ".join(unicodedata.normalize("NFKC", text).split()).casefold()


def merge_training_rows(
    train: list[dict[str, str]],
    additions: list[dict[str, str]],
) -> tuple[list[dict[str, str]], dict[str, int]]:
    """승인 자료만 합치고 설명 중복·정답 충돌을 차단한다."""
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


def train_setfit(rows: list[dict[str, str]], output_dir: Path,
                 steps: int, report: dict) -> None:
    """한국어 지원 문장 모델을 수입 자료로 학습해 별도 저장한다."""
    os.environ.setdefault("HF_HOME", str(BASE_DIR / "models/.hf_cache"))
    from datasets import Dataset
    from setfit import SetFitModel, Trainer, TrainingArguments
    import torch

    # CPU 시험이 다른 작업을 지나치게 점유하지 않도록 스레드를 제한한다.
    torch.set_num_threads(min(4, os.cpu_count() or 1))
    labels = sorted({row["category_code"] for row in rows})
    dataset = Dataset.from_dict({
        "text": [row["description"] for row in rows],
        "label": [labels.index(row["category_code"]) for row in rows],
    })
    output_dir.parent.mkdir(parents=True, exist_ok=True)
    lock = output_dir.parent / f".{output_dir.name}.train.lock"
    with lock.open("x", encoding="utf-8") as file:
        file.write("training setfit")
    try:
        with tempfile.TemporaryDirectory(prefix="setfit-trial-", dir=output_dir.parent) as temp:
            model = SetFitModel.from_pretrained(SETFIT_BASE_MODEL, labels=labels, device="cpu")
            arguments = TrainingArguments(
                output_dir=str(Path(temp) / "checkpoints"),
                batch_size=(8, 16), num_epochs=(1, 1), num_iterations=2,
                max_steps=steps, max_length=64,
                report_to="none", save_strategy="no", eval_strategy="no",
                logging_steps=5, seed=42,
            )
            limit = "전체 1에포크" if steps == -1 else f"최대 {steps}단계"
            print(f"SetFit 시험 학습: {len(rows)}건 / 언어 모델 {limit}", flush=True)
            Trainer(model=model, args=arguments, train_dataset=dataset).train()
            saved = Path(temp) / "model"
            model.save_pretrained(str(saved))
            restored = SetFitModel.from_pretrained(str(saved), device="cpu")
            probe = [rows[0]["description"]]
            if list(restored.predict(probe)) != list(model.predict(probe)):
                raise ValueError("SetFit 저장 전후 예측 결과가 다릅니다.")
            report.update({"selected_model": "setfit", "base_model": SETFIT_BASE_MODEL,
                           "trial_max_steps": steps, "service_model_replaced": False})
            (saved / "model_report.json").write_text(
                json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8",
            )
            # 서비스 모델 폴더와 분리된 시험 결과만 반영한다.
            shutil.copytree(saved, output_dir, dirs_exist_ok=True)
    finally:
        lock.unlink()
    print(f"SetFit 시험 모델 저장: {output_dir}")


def main() -> None:
    """추가 CSV가 있으면 합치고, 없으면 기본 CSV만 학습한다."""
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--overwrite", action="store_true", help="기존 모델 덮어쓰기")
    parser.add_argument("--data-dir", type=Path, default=DATA_DIR)
    parser.add_argument("--output-dir", type=Path)
    parser.add_argument("--setfit", action="store_true", help="기존 모델 대신 SetFit 별도 시험 학습")
    parser.add_argument("--setfit-steps", type=int, default=-1, help="-1: 전체 1에포크, 양수: 최대 학습 단계")
    parser.add_argument("--incoming-dir", type=Path,
                        default=BASE_DIR / "data/income/user_approved",
                        help="추가 CSV 폴더. 없거나 비어 있으면 기본 자료만 학습")
    parser.add_argument("--selected-model", choices=("logistic_regression", "linear_svc"),
                        default="logistic_regression", help="서버에서 사용할 모델 (자동 비교 선택 아님)")
    args = parser.parse_args()
    args.output_dir = args.output_dir or (SETFIT_DIR if args.setfit else OUTPUT_DIR)
    if args.setfit and args.output_dir.resolve() == OUTPUT_DIR.resolve():
        parser.error("SetFit 시험은 서비스 모델 폴더에 저장할 수 없습니다.")
    if args.setfit_steps == 0 or args.setfit_steps < -1:
        parser.error("--setfit-steps는 -1 또는 1 이상이어야 합니다.")
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
    rows, counts = merge_training_rows(base_rows, additions)
    if any(path.read_bytes() != data for path, data in contents.items()):
        raise ValueError("읽는 중 학습 CSV가 변경됐습니다. 다시 실행해주세요.")
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
        "selection_rule": "explicit_selection_not_test_score",
        "selected_model": args.selected_model,
    }
    if args.setfit:
        train_setfit(rows, args.output_dir, args.setfit_steps, report)
        return

    candidates = {
        "logistic_regression": LogisticRegression(max_iter=2000, random_state=42),
        "linear_svc": LinearSVC(max_iter=5000, random_state=42),
    }
    print(f"기본 {len(base_rows)}건 + 추가 {counts['added']}건 / 중복 제외 {counts['duplicates']}건")
    args.output_dir.parent.mkdir(parents=True, exist_ok=True)
    lock = args.output_dir.parent / f".{args.output_dir.name}.train.lock"
    # 같은 모델 폴더를 대상으로 동시에 학습하지 않는다.
    with lock.open("x", encoding="utf-8") as file:
        file.write("training")
    try:
        with tempfile.TemporaryDirectory(prefix="income-training-", dir=args.output_dir.parent) as temp:
            staging_dir = Path(temp)
            for name, classifier in candidates.items():
                pipeline = build_pipeline(classifier)
                pipeline.fit(descriptions, categories)
                path = staging_dir / f"{name}_bundle.pkl"
                joblib.dump({
                    "pipeline": pipeline, "model_name": name,
                    "transaction_type": "INCOME", "input_fields": ["description"],
                    "labels": labels, "sklearn_version": sklearn.__version__,
                    "dataset_review_status": report["dataset_review_status"],
                }, path)
                restored = joblib.load(path)["pipeline"]
                if list(restored.predict(descriptions)) != list(pipeline.predict(descriptions)):
                    raise ValueError("모델 저장 전후 예측 결과가 다릅니다.")
                print(f"{name}: 전체 {len(rows)}건 학습·저장 완료", flush=True)

            # 학습 자료를 다시 맞힌 점수를 시험 정확도처럼 기록하지 않는다.
            (staging_dir / "model_report.json").write_text(
                json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8",
            )
            # 두 모델 학습이 모두 성공한 뒤에만 결과를 반영한다.
            args.output_dir.mkdir(parents=True, exist_ok=True)
            for name in candidates:
                (staging_dir / f"{name}_bundle.pkl").replace(args.output_dir / f"{name}_bundle.pkl")
            (staging_dir / "model_report.json").replace(args.output_dir / "model_report.json")
    finally:
        lock.unlink()
    print(f"서버 선택 모델: {args.selected_model} (새 시험 점수로 선택한 결과 아님)")


if __name__ == "__main__":
    try:
        main()
    except (ValueError, OSError) as exc:
        raise SystemExit(f"학습 중단: {exc}") from exc
