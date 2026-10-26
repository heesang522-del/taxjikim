"""새 설명을 직접 입력하거나 CSV로 읽어 두 지출 모델의 예측·점수를 확인한다."""

import argparse
import csv
import sys
from pathlib import Path

import joblib

from train_expense import (
    BASE_DIR, DATA_DIR, OUTPUT_DIR, SETFIT_DIR,
    SIMPLE_OUTPUT_DIR, SIMPLE_SETFIT_DIR,
    COMBINED_OUTPUT_DIR, COMBINED_SETFIT_DIR, read_csv,
)


# 직접 시험도 서버와 동일한 미분류 함수를 사용한다.
sys.path.insert(0, str(BASE_DIR))
from app.expense_classifier import CLASSIFICATION_LIMITS, classify_descriptions

TEST_DATA_PATH = BASE_DIR / "data/expense/testing/test_data.csv"


def save_test_description(description: str, path: Path) -> None:
    """직접 입력한 새 설명만 시험 CSV에 추가하고 정답은 비워둔다."""
    description = description.strip()
    if not description:
        return
    exists = path.exists() and path.stat().st_size > 0
    fields = ["description", "expected_category"]
    if exists:
        with path.open(encoding="utf-8-sig", newline="") as file:
            reader = csv.DictReader(file)
            fields = reader.fieldnames or []
            if "description" not in fields or "expected_category" not in fields:
                raise ValueError("시험 CSV에 description, expected_category 열이 필요합니다.")
            if any((row.get("description") or "").strip() == description for row in reader):
                print("  저장: 이미 시험 CSV에 있는 설명입니다.")
                return
    path.parent.mkdir(parents=True, exist_ok=True)
    # 예측 카테고리를 정답으로 저장하지 않는다. 시험 입력은 학습과 분리한다.
    with path.open("a", encoding="utf-8" if exists else "utf-8-sig", newline="") as file:
        writer = csv.DictWriter(file, fieldnames=fields)
        if not exists:
            writer.writeheader()
        writer.writerow({"description": description, "expected_category": ""})
    print(f"  시험용 설명 저장: {path}")


def show_predictions(description: str, models: dict, expected: str = "") -> None:
    """같은 설명에 대한 두 모델의 카테고리와 서로 다른 점수 종류를 출력한다."""
    print(f"\n설명: {description or '(빈 설명)'}")
    if expected:
        print(f"직접 적은 정답: {expected}")
    for name, pipeline in models.items():
        result = classify_descriptions(pipeline, name, [description])[0]
        if result["score"] is None:
            print(f"  {name}: UNCLASSIFIED (설명 없음)")
            continue
        score_type = "결정 점수 (확률 아님)" if name == "linear_svc" else "분류 점수 (정확도 아님)"
        print(f"  {name}: {result['category']} | {score_type} {result['score']:.3f} | 2위와 차이 {result['gap']:.3f}")
        if result["reason"]:
            print(f"    원래 예측: {result['predicted_category']} / 미분류 사유: {result['reason']}")



def main() -> None:
    """CSV 일괄 시험·한 문장 시험·반복 직접 입력 중 선택해 실행한다."""
    parser = argparse.ArgumentParser(description=__doc__)
    inputs = parser.add_mutually_exclusive_group()
    inputs.add_argument("--description", help="바로 확인할 거래 설명")
    inputs.add_argument("--csv", type=Path, help="description,expected_category 열의 시험 CSV")
    datasets = parser.add_mutually_exclusive_group()
    datasets.add_argument("--simple", action="store_true", help="단순화 데이터로 학습한 비교 모델 사용")
    datasets.add_argument("--combined", action="store_true", help="기존·단순화 합친 데이터 모델 사용")
    parser.add_argument("--model-dir", type=Path)
    parser.add_argument("--setfit", action="store_true", help="별도 학습한 SetFit 결과도 함께 확인")
    parser.add_argument("--setfit-dir", type=Path)
    args = parser.parse_args()
    default_model = COMBINED_OUTPUT_DIR if args.combined else (SIMPLE_OUTPUT_DIR if args.simple else OUTPUT_DIR)
    default_setfit = COMBINED_SETFIT_DIR if args.combined else (SIMPLE_SETFIT_DIR if args.simple else SETFIT_DIR)
    args.model_dir = args.model_dir or default_model
    args.setfit_dir = args.setfit_dir or default_setfit
    models = {
        name: joblib.load(args.model_dir / f"{name}_bundle.pkl")["pipeline"]
        for name in ("logistic_regression", "linear_svc")
    }
    if args.setfit:
        if not (args.setfit_dir / "config_setfit.json").exists():
            parser.error("SetFit 모델이 없습니다. 먼저 train_expense.py --setfit을 실행해주세요.")
        from setfit import SetFitModel
        import torch
        torch.set_num_threads(4)
        models["setfit"] = SetFitModel.from_pretrained(str(args.setfit_dir), device="cpu")
    print("직접 입력한 설명은 시험 CSV에 저장합니다. 학습에는 사용하지 않습니다.")
    print("두 모델의 점수는 종류가 달라 숫자 크기를 서로 비교하지 않습니다.")
    for name in models:
        score, gap = CLASSIFICATION_LIMITS[name]
        print(f"미분류 기준 {name}: 최고 점수 < {score} 또는 1·2위 차이 < {gap}")
    if args.csv:
        rows = read_csv(args.csv)
        if not rows or any("description" not in row for row in rows):
            parser.error("시험 CSV에 description 열과 시험할 행이 필요합니다.")
        # 아는 문장을 시험하는 경우를 숨기지 않고 표시한다.
        source = DATA_DIR / ("training_data_simple.csv" if args.simple else "training_data.csv")
        if args.combined:
            source = DATA_DIR / "training_data_combined.csv"
        known = {row["description"].strip() for row in read_csv(source)} if source.exists() else set()
        for row in rows:
            description = (row.get("description") or "").strip()
            if description in known:
                print("\n[주의: 기본 학습 CSV에 이미 있는 설명]")
            show_predictions(description, models, row.get("expected_category", "") or "")
    elif args.description is not None:
        show_predictions(args.description, models)
        save_test_description(args.description, TEST_DATA_PATH)
    else:
        print("설명을 입력하세요. 종료하려면 Enter 또는 Ctrl+C를 누르세요.")
        while True:
            try:
                description = input("\n거래 설명 > ").strip()
            except (EOFError, KeyboardInterrupt):
                print()
                break
            if not description:
                break
            show_predictions(description, models)
            save_test_description(description, TEST_DATA_PATH)


if __name__ == "__main__":
    main()
