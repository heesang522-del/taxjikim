"""검토한 지출 데이터를 거래 의미 그룹 단위로 분할한다."""

import argparse
import csv
import hashlib
import json
import random
from collections import Counter
from pathlib import Path


DATA_DIR = Path(__file__).resolve().parents[1] / "data/expense/category_dummy_v3"
SEED = 42

# 각 목록은 대표 설명 01~20에 대응한다. 같은 숫자는 비슷한 거래 의미다.
# 분야명이나 결제 표현만 다른 자료가 학습·평가 양쪽에 섞이지 않게 묶는다.
FAMILIES = {
    "SOFTWARE_LICENSE": [1, 2, 3, 4, 5, 6, 3, 7, 2, 1,
                         1, 1, 1, 3, 4, 2, 2, 2, 7, 5],
    "EQUIPMENT": [1, 2, 3, 4, 5, 1, 2, 6, 7, 8,
                  1, 1, 2, 1, 5, 9, 4, 10, 8, 11],
    "TELECOMMUNICATION": [1, 2, 3, 1, 3, 3, 2, 2, 3, 2,
                          1, 1, 2, 2, 2, 3, 3, 3, 3, 3],
    "TRANSPORT": [1, 2, 3, 4, 5, 6, 1, 4, 2, 3,
                  1, 1, 2, 3, 4, 2, 6, 7, 5, 8],
    "SUPPLIES_PROPS": [1, 2, 3, 4, 1, 1, 5, 2, 2, 1,
                       1, 2, 3, 4, 1, 5, 5, 5, 2, 2],
    "SPACE_RENTAL": [1, 2, 3, 4, 5, 6, 4, 1, 6, 1,
                     1, 2, 3, 4, 1, 1, 6, 6, 6, 6],
    "OUTSOURCING": [1, 2, 3, 3, 4, 2, 1, 1, 3, 4,
                    1, 5, 2, 3, 6, 7, 4, 1, 4, 6],
    "AD_PROMOTION": [1, 2, 3, 4, 5, 1, 2, 3, 4, 5,
                     1, 2, 3, 3, 4, 4, 4, 5, 2, 4],
    "OTHER": [1, 2, 3, 4, 5, 1, 2, 3, 4, 5,
              1, 1, 2, 2, 3, 3, 4, 4, 5, 5],
}


def read_csv(path: Path) -> list[dict[str, str]]:
    with path.open(encoding="utf-8-sig", newline="") as file:
        return list(csv.DictReader(file))


def write_csv(path: Path, rows: list[dict[str, str]]) -> None:
    with path.open("w", encoding="utf-8-sig", newline="") as file:
        writer = csv.DictWriter(file, fieldnames=list(rows[0]))
        writer.writeheader()
        writer.writerows(rows)


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--overwrite", action="store_true")
    args = parser.parse_args()
    output_dir = DATA_DIR / "splits"
    if output_dir.exists() and not args.overwrite:
        parser.error("분할 폴더가 이미 있습니다. 재생성하려면 --overwrite를 지정하세요.")

    source = DATA_DIR / "labeled_candidates.csv"
    rows = read_csv(source)
    expected_groups = {
        f"{category.lower()}_{index:02d}"
        for category, numbers in FAMILIES.items()
        for index in range(1, len(numbers) + 1)
    }
    # 이 대응표는 v3 전용이다. 대표 설명이 바뀌면 대응표도 다시 검토한다.
    if {row["group_id"] for row in rows} != expected_groups:
        raise ValueError("v3의 예상 그룹과 다릅니다. 먼저 데이터 생성과 의미 그룹 정의를 확인하세요.")

    rng = random.Random(SEED)
    assignments = {}
    for category, numbers in FAMILIES.items():
        families = sorted(set(numbers))
        rng.shuffle(families)
        # 비율을 억지로 맞추지 않고 카테고리마다 평가용 의미 그룹을 확보한다.
        for index, family in enumerate(families):
            assignments[(category, family)] = (
                "validation" if index == 0 else "test" if index == 1 else "train"
            )

    splits = {name: [] for name in ("train", "validation", "test")}
    for row in rows:
        category = row["category_code"]
        index = int(row["group_id"].rsplit("_", 1)[1]) - 1
        family = FAMILIES[category][index]
        row["semantic_group_id"] = f"{category.lower()}_family_{family:02d}"
        splits[assignments[(category, family)]].append(row)

    for field in ("sample_id", "group_id", "semantic_group_id", "description"):
        seen = set()
        for name, items in splits.items():
            values = {row[field] for row in items}
            if seen & values:
                raise ValueError(f"분할 사이에 {field}가 겹칩니다: {name}")
            seen.update(values)
    if len({row['sample_id'] for row in rows}) != len(rows):
        raise ValueError("중복 sample_id가 있습니다.")
    if any(set(row['category_code'] for row in items) != set(FAMILIES)
           for items in splits.values()):
        raise ValueError("일부 분할에 카테고리가 빠졌습니다.")

    # 정보 부족 자료도 검증·시험으로 나눠 시험 자료를 임계값 조정에 쓰지 않는다.
    needs_info = read_csv(DATA_DIR / "needs_info.csv")
    rng.shuffle(needs_info)
    middle = len(needs_info) // 2
    output_dir.mkdir(parents=True, exist_ok=True)
    for name, items in splits.items():
        write_csv(output_dir / f"{name}.csv", items)
    write_csv(output_dir / "needs_info_validation.csv", needs_info[:middle])
    write_csv(output_dir / "needs_info_test.csv", needs_info[middle:])

    report = {
        "seed": SEED,
        "source_sha256": hashlib.sha256(source.read_bytes()).hexdigest(),
        "split_rule": "one_semantic_family_per_category_for_validation_and_test",
        "review_note": "Representative labels discussed with user; CSV review_status unchanged.",
        "counts": {name: len(items) for name, items in splits.items()},
        "category_counts": {
            name: dict(Counter(row['category_code'] for row in items))
            for name, items in splits.items()
        },
        "semantic_family_counts": {
            name: len({row['semantic_group_id'] for row in items})
            for name, items in splits.items()
        },
        "needs_info_counts": {"validation": middle, "test": len(needs_info) - middle},
        "limitation": "Small synthetic dataset; manually assigned families do not guarantee absence of all semantic similarity.",
    }
    (output_dir / "split_manifest.json").write_text(
        json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8",
    )
    for name, items in splits.items():
        print(f"{name}: {len(items)}건")
    print("분할 간 ID·그룹·동일 설명 겹침 없음. 모델 학습은 아직 하지 않았습니다.")


if __name__ == "__main__":
    main()
