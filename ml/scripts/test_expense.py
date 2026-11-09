"""새 설명을 직접 입력하거나 CSV로 읽어 지출 LinearSVC의 예측·점수를 확인한다."""

import argparse
import csv
import sys
from pathlib import Path

import joblib

from train_expense import BASE_DIR, DATA_DIR, OUTPUT_DIR, read_csv, SIMPLE_OUTPUT_DIR, COMBINED_OUTPUT_DIR


# 직접 시험도 서버와 동일한 미분류 함수를 사용한다.
sys.path.insert(0, str(BASE_DIR))
from app.expense_classifier import CLASSIFICATION_LIMITS, classify_descriptions

TEST_DATA_PATH = BASE_DIR / "data/expense/testing/test_data.csv"


# 직접 시험한 새 설명을 시험 CSV에 저장한다.
def save_test_description(description: str, path: Path) -> None:
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


# LinearSVC 분류 결과와 결정 점수, 미분류 사유를 출력한다.
def show_predictions(description: str, model: object, expected: str = "") -> None:
    print(f"\n설명: {description or '(빈 설명)'}")
    if expected:
        print(f"직접 적은 정답: {expected}")
    result = classify_descriptions(model, [description])[0]
    if result["score"] is None:
        print("  linear_svc: UNCLASSIFIED (설명 없음)")
        return
    print(f"  linear_svc: {result['category']} | 결정 점수 (확률 아님) {result['score']:.3f} | 2위와 차이 {result['gap']:.3f}")
    if result["reason"]:
        print(f"    원래 예측: {result['predicted_category']} / 미분류 사유: {result['reason']}")


# 명령 옵션에 맞게 모델을 불러와 설명을 시험한다.
def main() -> None:
    # 터미널에서 실행할 때 사용할 옵션을 설정한다.
    parser = argparse.ArgumentParser(description=__doc__)
    inputs = parser.add_mutually_exclusive_group()
    inputs.add_argument("--description", help="바로 확인할 거래 설명")
    inputs.add_argument("--csv", type=Path, help="description,expected_category 열의 시험 CSV")
    datasets = parser.add_mutually_exclusive_group()
    datasets.add_argument("--simple", action="store_true", help="단순화 데이터로 학습한 비교 모델 사용")
    datasets.add_argument("--combined", action="store_true", help="기존·단순화 합친 데이터 모델 사용")
    parser.add_argument("--model-dir", type=Path)
    args = parser.parse_args()
    default_model = OUTPUT_DIR
    if args.simple:
        default_model = SIMPLE_OUTPUT_DIR
    elif args.combined:
        default_model = COMBINED_OUTPUT_DIR
    args.model_dir = args.model_dir or default_model
    model_path = args.model_dir / "linear_svc_bundle.pkl"
    if not model_path.is_file():
        parser.error("LinearSVC 모델이 없습니다. 먼저 학습 스크립트를 실행해주세요.")
    model = joblib.load(model_path)["pipeline"]
    print("직접 입력한 설명은 시험 CSV에 저장합니다. 학습에는 사용하지 않습니다.")
    score, gap = CLASSIFICATION_LIMITS
    print(f"미분류 기준 linear_svc: 최고 점수 < {score} 또는 1·2위 차이 < {gap}")
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
            show_predictions(description, model, row.get("expected_category", "") or "")
    elif args.description is not None:
        show_predictions(args.description, model)
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
            show_predictions(description, model)
            save_test_description(description, TEST_DATA_PATH)


if __name__ == "__main__":
    main()
