"""지출 설명 데이터를 같은 그룹이 섞이지 않도록 train/test로 나눈다."""

import argparse
import csv
import hashlib
import json
from collections import Counter
from pathlib import Path

from sklearn.model_selection import train_test_split


DATA_DIR = Path(__file__).resolve().parents[1] / "data/expense/category_dummy_v4_draft"
SEED = 42
TEST_SIZE = 0.2


def read_csv(path: Path) -> list[dict[str, str]]:
    """설명과 정답이 있는 지출 후보 자료를 읽는다."""
    with path.open(encoding="utf-8-sig", newline="") as file:
        return list(csv.DictReader(file))


def write_csv(path: Path, rows: list[dict[str, str]]) -> None:
    """분할한 설명과 정답을 원래 열 그대로 저장한다."""
    with path.open("w", encoding="utf-8-sig", newline="") as file:
        writer = csv.DictWriter(file, fieldnames=list(rows[0]))
        writer.writeheader()
        writer.writerows(rows)


def split_rows(rows: list[dict[str, str]]) -> dict[str, list[dict[str, str]]]:
    """카테고리 비율을 유지하면서 같은 그룹 전체를 한쪽에 배치한다."""
    if not rows:
        raise ValueError("분할할 자료가 없습니다.")
    groups: dict[str, str] = {}
    for row in rows:
        if any(not row.get(field, "").strip() for field in (
            "sample_id", "group_id", "description", "category_code",
        )):
            raise ValueError("자료에 필수값이 빠져 있습니다.")
        group = row["group_id"]
        label = row["category_code"]
        if group in groups and groups[group] != label:
            raise ValueError("같은 그룹에 서로 다른 정답이 있습니다.")
        groups[group] = label

    if len({row["sample_id"] for row in rows}) != len(rows):
        raise ValueError("중복 sample_id가 있습니다.")
    # 같은 설명이 양쪽에 들어가 시험 점수가 부풀려지는 것을 막는다.
    descriptions = [" ".join(row["description"].split()).casefold() for row in rows]
    if len(set(descriptions)) != len(descriptions):
        raise ValueError("중복 설명이 있습니다. 원본 자료를 확인해주세요.")

    group_ids = sorted(groups)
    train_groups, test_groups = train_test_split(
        group_ids,
        test_size=TEST_SIZE,
        random_state=SEED,
        stratify=[groups[group] for group in group_ids],
    )
    train_set = set(train_groups)
    test_set = set(test_groups)
    splits = {
        "train": [row for row in rows if row["group_id"] in train_set],
        "test": [row for row in rows if row["group_id"] in test_set],
    }
    labels = set(groups.values())
    if any({row["category_code"] for row in items} != labels for items in splits.values()):
        raise ValueError("학습 또는 시험 자료에 카테고리가 빠졌습니다.")
    return splits


def main() -> None:
    """기존 원본을 유지하고 train/test 파일과 분할 요약을 생성한다."""
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--overwrite", action="store_true", help="기존 train/test 결과 덮어쓰기")
    args = parser.parse_args()
    source = DATA_DIR / "labeled_candidates.csv"
    output_dir = DATA_DIR / "train_test"
    if output_dir.exists() and not args.overwrite:
        parser.error("분할 결과가 이미 있습니다. 재생성하려면 --overwrite를 지정하세요.")

    rows = read_csv(source)
    splits = split_rows(rows)
    output_dir.mkdir(parents=True, exist_ok=True)
    for name, items in splits.items():
        write_csv(output_dir / f"{name}.csv", items)
        print(f"{name}: {len(items)}건")

    report = {
        "seed": SEED,
        "test_size": TEST_SIZE,
        "source_sha256": hashlib.sha256(source.read_bytes()).hexdigest(),
        "split_rule": "stratified_by_category_keep_group_id_together",
        "counts": {name: len(items) for name, items in splits.items()},
        "category_counts": {
            name: dict(Counter(row["category_code"] for row in items))
            for name, items in splits.items()
        },
        "limitation": "합성 정답 후보이며 group_id가 다른 유사한 의미까지 완전히 분리한 것은 아님",
    }
    (output_dir / "split_manifest.json").write_text(
        json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8",
    )
    print("별도 validation 없이 학습·시험 두 개로 분할했습니다.")
    print("경계 사례와 정보 부족 자료는 학습에 넣지 않았습니다.")


if __name__ == "__main__":
    main()
