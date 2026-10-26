"""새 설명을 직접 입력하거나 CSV로 읽어 두 지출 모델의 예측·점수를 확인한다."""

import argparse
from pathlib import Path

import joblib
import numpy as np

from train_expense import DATA_DIR, OUTPUT_DIR, read_csv


def show_predictions(description: str, models: dict, expected: str = "") -> None:
    """같은 설명에 대한 두 모델의 카테고리와 서로 다른 점수 종류를 출력한다."""
    print(f"\n설명: {description or '(빈 설명)'}")
    if expected:
        print(f"직접 적은 정답: {expected}")
    if not description.strip():
        print("두 모델: UNCLASSIFIED (설명 없음)")
        return
    for name, pipeline in models.items():
        if name == "logistic_regression":
            scores = pipeline.predict_proba([description])[0]
            score_type = "분류 점수"
        else:
            scores = pipeline.decision_function([description])[0]
            score_type = "결정 점수 (확률 아님)"
        order = np.argsort(scores)
        index = order[-1]
        category = pipeline.classes_[index]
        gap = scores[index] - scores[order[-2]]
        print(f"  {name}: {category} | {score_type} {scores[index]:.3f} | 2위와 차이 {gap:.3f}")
    print("  점수 기반 미분류 기준은 아직 미적용입니다.")


def main() -> None:
    """CSV 일괄 시험·한 문장 시험·반복 직접 입력 중 선택해 실행한다."""
    parser = argparse.ArgumentParser(description=__doc__)
    inputs = parser.add_mutually_exclusive_group()
    inputs.add_argument("--description", help="바로 확인할 거래 설명")
    inputs.add_argument("--csv", type=Path, help="description,expected_category 열의 시험 CSV")
    parser.add_argument("--model-dir", type=Path, default=OUTPUT_DIR)
    args = parser.parse_args()
    models = {
        name: joblib.load(args.model_dir / f"{name}_bundle.pkl")["pipeline"]
        for name in ("logistic_regression", "linear_svc")
    }
    print("예측만 실행합니다. 입력 내용은 저장하거나 학습하지 않습니다.")
    print("두 모델의 점수는 종류가 달라 숫자 크기를 서로 비교하지 않습니다.")
    if args.csv:
        rows = read_csv(args.csv)
        if not rows or any("description" not in row for row in rows):
            parser.error("시험 CSV에 description 열과 시험할 행이 필요합니다.")
        # 아는 문장을 시험하는 경우를 숨기지 않고 표시한다.
        source = DATA_DIR / "training_data.csv"
        known = {row["description"].strip() for row in read_csv(source)} if source.exists() else set()
        for row in rows:
            description = (row.get("description") or "").strip()
            if description in known:
                print("\n[주의: 기본 학습 CSV에 이미 있는 설명]")
            show_predictions(description, models, row.get("expected_category", "") or "")
    elif args.description is not None:
        show_predictions(args.description, models)
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


if __name__ == "__main__":
    main()
