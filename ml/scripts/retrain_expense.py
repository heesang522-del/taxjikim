"""초기 분할을 보존하고 승인된 추가 CSV를 누적해 지출 모델을 재학습한다."""

import argparse
import hashlib
import json
import os
import subprocess
import sys
import unicodedata
from datetime import datetime
from pathlib import Path
from zoneinfo import ZoneInfo

from split_expense_data import read_csv, write_csv
from train_expense import BASE_DIR, DATA_DIR, read_rows


FIELDS = ("sample_id", "description", "category_code", "group_id", "review_status")


def normalized(text: str) -> str:
    """공백·대소문자·유니코드 표현 차이로 중복이 누락되지 않게 한다."""
    return " ".join(unicodedata.normalize("NFKC", text).split()).casefold()


def merge_training_rows(
    train: list[dict[str, str]],
    test: list[dict[str, str]],
    additions: list[dict[str, str]],
) -> tuple[list[dict[str, str]], dict[str, int]]:
    """승인 자료만 합치고 시험자료 중복·정답 충돌을 차단한다."""
    labels = {row["category_code"] for row in train}
    protected = {
        field: {normalized(row[field]) for row in test}
        for field in ("sample_id", "group_id", "description")
    }
    seen: dict[str, dict[str, str]] = {field: {} for field in protected}
    merged = []
    counts = {"added": 0, "duplicates": 0}
    for index, original in enumerate(train + additions):
        row = {field: original.get(field, "").strip() for field in FIELDS}
        if any(not row[field] for field in FIELDS):
            raise ValueError("CSV 필수값이 비어 있습니다: " + str(index + 1))
        is_addition = index >= len(train)
        if is_addition and row["review_status"] != "APPROVED":
            raise ValueError("추가 CSV는 사람이 검토한 APPROVED 행만 허용합니다.")
        label = row["category_code"]
        if label not in labels:
            raise ValueError(f"지원하지 않는 지출 카테고리: {label}")
        keys = {field: normalized(row[field]) for field in protected}
        if any(keys[field] in protected[field] for field in protected):
            raise ValueError("시험자료와 ID·그룹·설명이 겹칩니다. 추가 CSV를 확인하세요.")
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


def run(initial_dir: Path, incoming_dir: Path, runs_dir: Path) -> None:
    """입력 스냅샷을 남기고 기존 학습 명령으로 새 모델을 생성한다."""
    incoming_dir.mkdir(parents=True, exist_ok=True)
    runs_dir.mkdir(parents=True, exist_ok=True)
    lock = runs_dir / ".retrain.lock"
    # 수동 실행과 스케줄러가 동시에 같은 입력으로 학습하는 것을 막는다.
    with lock.open("x", encoding="utf-8") as file:
        file.write(str(os.getpid()))
    try:
        paths = [initial_dir / "labeled_candidates.csv"] + [
            initial_dir / "train_test" / name
            for name in ("train.csv", "test.csv", "split_manifest.json")
        ]
        paths += sorted(incoming_dir.glob("*.csv"))
        contents = {path: path.read_bytes() for path in paths}
        hashes = {str(path.resolve()): hashlib.sha256(data).hexdigest() for path, data in contents.items()}
        fingerprint = hashlib.sha256(json.dumps(hashes, sort_keys=True).encode()).hexdigest()
        for manifest in runs_dir.glob("*/run_manifest.json"):
            previous = json.loads(manifest.read_text(encoding="utf-8"))
            if previous.get("fingerprint") == fingerprint and previous.get("status") == "COMPLETED":
                print("입력 CSV가 이전 성공 실행과 같습니다. 재학습을 생략합니다.")
                return
        split_meta = json.loads(contents[initial_dir / "train_test/split_manifest.json"])
        if hashes[str((initial_dir / "labeled_candidates.csv").resolve())] != split_meta["source_sha256"]:
            raise ValueError("초기 원본과 분할 정보가 다릅니다. 기존 분할을 확인하세요.")
        train = read_rows(initial_dir / "train_test/train.csv")
        test = read_rows(initial_dir / "train_test/test.csv")
        additions = []
        for path in sorted(incoming_dir.glob("*.csv")):
            additions.extend(read_csv(path))
        merged, counts = merge_training_rows(train, test, additions)
        if any(path.read_bytes() != data for path, data in contents.items()):
            raise ValueError("준비 중 입력 파일이 변경됐습니다. 다시 실행하세요.")
        run_id = datetime.now(ZoneInfo("Asia/Seoul")).strftime("%Y%m%d_%H%M%S_%f")
        run_dir = runs_dir / run_id
        data_dir = run_dir / "dataset"
        split_dir = data_dir / "train_test"
        split_dir.mkdir(parents=True)
        write_csv(data_dir / "labeled_candidates.csv", merged + test)
        write_csv(split_dir / "train.csv", merged)
        write_csv(split_dir / "test.csv", test)
        split_meta["source_sha256"] = hashlib.sha256((data_dir / "labeled_candidates.csv").read_bytes()).hexdigest()
        split_meta["counts"] = {"train": len(merged), "test": len(test)}
        # 누적 자료로 매번 다시 분할하지 않고 초기 시험 세트를 고정한다.
        split_meta["split_rule"] = "initial_test_frozen_additions_train_only"
        split_meta.pop("category_counts", None)
        (split_dir / "split_manifest.json").write_text(json.dumps(split_meta, ensure_ascii=False, indent=2), encoding="utf-8")
        manifest = {
            "status": "RUNNING", "fingerprint": fingerprint, "input_sha256": hashes,
            "initial_train_count": len(train), "test_count": len(test), **counts,
            "limitation": "초기 합성 후보는 PENDING이며, 반복 사용 시험 점수는 독립적인 최종 평가가 아님. 의미가 유사한 설명의 누출은 수동 검토 필요.",
        }
        manifest_path = run_dir / "run_manifest.json"
        manifest_path.write_text(json.dumps(manifest, ensure_ascii=False, indent=2), encoding="utf-8")
        try:
            subprocess.run([
                sys.executable, str(BASE_DIR / "scripts/train_expense.py"),
                "--data-dir", str(data_dir), "--output-dir", str(run_dir / "model"),
            ], check=True)
        except Exception:
            manifest["status"] = "FAILED"
            raise
        else:
            manifest["status"] = "COMPLETED"
        finally:
            manifest_path.write_text(json.dumps(manifest, ensure_ascii=False, indent=2), encoding="utf-8")
        print(f"재학습 완료: {run_dir}")
        print("운영 모델은 유지했습니다. 평가 결과를 검토한 뒤 별도로 교체하세요.")
    finally:
        lock.unlink()


def main() -> None:
    """수동·외부 스케줄러가 함께 사용할 실행 진입점이다."""
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--initial-dir", type=Path, default=DATA_DIR)
    parser.add_argument("--incoming-dir", type=Path, default=BASE_DIR / "data/expense/user_approved")
    parser.add_argument("--runs-dir", type=Path, default=BASE_DIR / "models/expense_retraining")
    args = parser.parse_args()
    try:
        run(args.initial_dir.resolve(), args.incoming_dir.resolve(), args.runs_dir.resolve())
    except (ValueError, OSError, subprocess.CalledProcessError) as exc:
        parser.exit(1, f"재학습 중단: {exc}\n")


if __name__ == "__main__":
    main()
