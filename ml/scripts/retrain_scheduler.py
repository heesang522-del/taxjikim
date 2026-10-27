"""수입·지출 CSV가 바뀌었을 때 기존 학습 프로그램을 주기적으로 실행한다."""

import argparse
import hashlib
import json
import subprocess
import sys
import time
from datetime import datetime
from pathlib import Path
from zoneinfo import ZoneInfo


BASE_DIR = Path(__file__).resolve().parents[1]
SAVE_DIR = BASE_DIR / "models/retraining"
CHECK_INTERVAL_MINUTES = 60


# transaction_type: income 또는 expense, 반환값: 확인할 CSV 경로 목록
def get_data_files(transaction_type: str) -> list[Path]:
    """기본 학습 CSV와 추가 학습 CSV의 위치를 모은다."""
    data_dir = BASE_DIR / "data" / transaction_type
    files = [data_dir / "training/training_data.csv"]

    for path in sorted((data_dir / "user_approved").glob("*.csv")):
        files.append(path)

    return files


# 반환값: 파일 경로와 내용 식별값을 짝지은 딕셔너리
def get_data_versions(files: list[Path]) -> dict[str, str]:
    """CSV 내용이 지난 학습 때와 같은지 비교할 식별값을 만든다."""
    versions = {}

    for path in files:
        with path.open("rb") as file:
            contents = file.read()

        # 해시는 파일 내용을 나타내는 식별값이다. 내용이 바뀌면 값도 달라진다.
        version = hashlib.sha256(contents).hexdigest()
        versions[str(path.relative_to(BASE_DIR))] = version

    return versions


def read_last_training(path: Path) -> dict:
    """지난번에 성공한 학습의 데이터 식별값과 시각을 읽는다."""
    if not path.exists():
        return {}

    with path.open(encoding="utf-8") as file:
        record = json.load(file)

    if not isinstance(record, dict):
        raise ValueError(f"학습 기록 형식을 확인해주세요: {path}")

    return record


def run_training(transaction_type: str, output_dir: Path) -> None:
    """현재 가상환경의 Python으로 기존 학습 파일을 실행한다."""
    script = BASE_DIR / "scripts" / f"train_{transaction_type}.py"
    command = [
        sys.executable,
        str(script),
        "--output-dir",
        str(output_dir),
        "--overwrite",
    ]

    # subprocess.run: 터미널 명령을 별도 프로그램으로 실행하고 끝날 때까지 기다린다.
    # check=True: 학습 프로그램이 실패하면 예외로 알려준다.
    subprocess.run(command, cwd=BASE_DIR.parent, check=True)


def check_and_train(transaction_type: str) -> bool:
    """한 종류의 CSV 변경을 확인하고 필요할 때만 재학습한다."""
    output_dir = SAVE_DIR / transaction_type / "classifier"
    record_path = SAVE_DIR / f"{transaction_type}_last_training.json"

    try:
        files = get_data_files(transaction_type)
        versions = get_data_versions(files)
        last_training = read_last_training(record_path)

        # 기록뿐 아니라 결과 파일도 있어야 학습을 생략한다.
        model_files_exist = True
        for name in ["logistic_regression_bundle.pkl", "linear_svc_bundle.pkl", "model_report.json"]:
            if not (output_dir / name).is_file():
                model_files_exist = False

        if last_training.get("data_versions") == versions and model_files_exist:
            print(f"[{transaction_type}] 데이터 변경 없음: 학습 생략", flush=True)
            return True

        print(f"[{transaction_type}] 데이터 변경 또는 첫 실행: 학습 시작", flush=True)
        started = time.monotonic()
        run_training(transaction_type, output_dir)

        # 학습 도중 CSV가 바뀌면 성공 기록을 남기지 않고 다음 확인 때 다시 학습한다.
        if get_data_versions(get_data_files(transaction_type)) != versions:
            print(f"[{transaction_type}] 학습 중 데이터 변경: 다음 확인 때 재시도", flush=True)
            return False

        record = {
            "data_versions": versions,
            "trained_at": datetime.now(ZoneInfo("Asia/Seoul")).isoformat(),
            "duration_seconds": round(time.monotonic() - started, 2),
        }
        with record_path.open("w", encoding="utf-8") as file:
            json.dump(record, file, ensure_ascii=False, indent=2)

        print(f"[{transaction_type}] 학습 완료: {record['duration_seconds']}초", flush=True)
        return True

    except (OSError, ValueError, subprocess.CalledProcessError) as error:
        # 성공 기록을 갱신하지 않으므로 다음 주기에 다시 시도한다.
        print(f"[{transaction_type}] 학습 실패: {error}", flush=True)
        return False


def main() -> None:
    """한 번 확인하거나, 지정한 간격으로 수입·지출 학습을 반복한다."""
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--once", action="store_true", help="한 번만 확인하고 종료")
    parser.add_argument("--interval-minutes", type=int, default=CHECK_INTERVAL_MINUTES,
                        help="확인 간격(분), 기본 60분")
    args = parser.parse_args()
    if args.interval_minutes < 1:
        parser.error("확인 간격은 1분 이상이어야 합니다.")

    SAVE_DIR.mkdir(parents=True, exist_ok=True)
    lock_path = SAVE_DIR / "scheduler.lock"
    try:
        # x는 파일이 이미 있으면 실패한다. 스케줄러 중복 실행을 막는다.
        with lock_path.open("x", encoding="utf-8") as file:
            file.write("retraining scheduler running")
    except FileExistsError:
        raise SystemExit("이미 실행 중입니다. 종료 후에도 잠금 파일이 남았다면 실행 여부를 먼저 확인해주세요.")

    try:
        while True:
            success = True
            for transaction_type in ["income", "expense"]:
                if not check_and_train(transaction_type):
                    success = False

            if args.once:
                if not success:
                    raise SystemExit(1)
                return

            print(f"{args.interval_minutes}분 후 다시 확인합니다. 종료: Ctrl+C", flush=True)
            # sleep 동안 기다렸다가 위의 반복문으로 돌아간다.
            time.sleep(args.interval_minutes * 60)
    except KeyboardInterrupt:
        print("스케줄러를 종료합니다.")
    finally:
        lock_path.unlink()


if __name__ == "__main__":
    main()
