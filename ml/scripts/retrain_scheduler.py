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


# ML 폴더와 학습 결과 저장 위치를 정한다.
BASE_DIR = Path(__file__).resolve().parents[1]
SAVE_DIR = BASE_DIR / "models/retraining"

# 수입·지출 확인이 끝나면 기본 60분 후 다시 확인한다.
CHECK_INTERVAL_MINUTES = 60


# 기본 자료와 추가 자료의 CSV 경로를 모은다.
def get_data_files(transaction_type: str) -> list[Path]:
    data_dir = BASE_DIR / "data" / transaction_type
    files = [data_dir / "training/training_data.csv"]

    for path in sorted((data_dir / "user_approved").glob("*.csv")):
        files.append(path)

    return files


# CSV 내용의 변경을 비교할 식별값을 만든다.
def get_data_versions(files: list[Path]) -> dict[str, str]:
    versions = {}

    for path in files:
        with path.open("rb") as file:
            contents = file.read()

        # 파일 내용의 식별값을 만들어 지난 학습 자료와 비교한다.
        version = hashlib.sha256(contents).hexdigest()
        versions[str(path.relative_to(BASE_DIR))] = version

    return versions


# 마지막으로 성공한 학습 기록을 읽는다.
def read_last_training(path: Path) -> dict:
    if not path.exists():
        return {}

    with path.open(encoding="utf-8") as file:
        record = json.load(file)

    if not isinstance(record, dict):
        raise ValueError(f"학습 기록 형식을 확인해주세요: {path}")

    return record


# 기존 학습 프로그램을 실행하고 종료를 기다린다.
def run_training(transaction_type: str, output_dir: Path) -> None:
    script = BASE_DIR / "scripts" / f"train_{transaction_type}.py"
    command = [
        sys.executable,
        str(script),
        "--output-dir",
        str(output_dir),
        "--overwrite",
    ]

    # 기존 학습 명령을 실행하고 실패하면 예외로 처리한다.
    subprocess.run(command, cwd=BASE_DIR.parent, check=True)


# 데이터가 바뀐 경우 학습하고 성공 기록을 저장한다.
def check_and_train(transaction_type: str) -> bool:
    output_dir = SAVE_DIR / transaction_type / "classifier"
    record_path = SAVE_DIR / f"{transaction_type}_last_training.json"

    try:
        files = get_data_files(transaction_type)
        versions = get_data_versions(files)
        last_training = read_last_training(record_path)

        # 이전 모델 파일이 모두 남아 있는지 확인한다.
        model_files_exist = True
        for name in ["logistic_regression_bundle.pkl", "linear_svc_bundle.pkl", "model_report.json"]:
            if not (output_dir / name).is_file():
                model_files_exist = False

        # 자료가 같고 모델도 있으면 재학습을 생략한다.
        if last_training.get("data_versions") == versions and model_files_exist:
            print(f"[{transaction_type}] 데이터 변경 없음: 학습 생략", flush=True)
            return True

        print(f"[{transaction_type}] 데이터 변경 또는 첫 실행: 학습 시작", flush=True)
        started = time.monotonic()
        run_training(transaction_type, output_dir)

        # 학습 중 자료가 바뀌면 성공 기록을 남기지 않고 다음에 재시도한다.
        if get_data_versions(get_data_files(transaction_type)) != versions:
            print(f"[{transaction_type}] 학습 중 데이터 변경: 다음 확인 때 재시도", flush=True)
            return False

        # 학습에 사용한 자료 정보와 성공 시각, 걸린 시간을 기록한다.
        record = {
            "data_versions": versions,
            "trained_at": datetime.now(ZoneInfo("Asia/Seoul")).isoformat(),
            "duration_seconds": round(time.monotonic() - started, 2),
        }
        with record_path.open("w", encoding="utf-8") as file:
            json.dump(record, file, ensure_ascii=False, indent=2)

        print(f"[{transaction_type}] 학습 완료: {record['duration_seconds']}초", flush=True)
        return True

    # 실패한 학습은 성공 기록을 갱신하지 않아 다음 주기에 다시 시도한다.
    except (OSError, ValueError, subprocess.CalledProcessError) as error:
        print(f"[{transaction_type}] 학습 실패: {error}", flush=True)
        return False


# 수입과 지출의 재학습 확인을 지정한 간격으로 반복한다.
def main() -> None:
    # 터미널에서 실행할 때 사용할 옵션을 설정한다.
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--once", action="store_true", help="한 번만 확인하고 종료")
    parser.add_argument("--interval-minutes", type=int, default=CHECK_INTERVAL_MINUTES,
                        help="확인 간격(분), 기본 60분")
    args = parser.parse_args()
    # 확인 간격이 1분 미만이면 실행하지 않는다.
    if args.interval_minutes < 1:
        parser.error("확인 간격은 1분 이상이어야 합니다.")

    # 학습 결과와 기록을 저장할 폴더를 준비한다.
    SAVE_DIR.mkdir(parents=True, exist_ok=True)
    try:
        # 수입·지출을 차례로 확인하고 지정한 간격으로 반복한다.
        while True:
            success = True
            for transaction_type in ["income", "expense"]:
                if not check_and_train(transaction_type):
                    success = False

            # 한 번 실행 옵션이면 성공·실패를 알리고 종료한다.
            if args.once:
                if not success:
                    raise SystemExit(1)
                return

            print(f"{args.interval_minutes}분 후 다시 확인합니다. 종료: Ctrl+C", flush=True)
            # 작업이 끝난 뒤 기본 60분을 기다린다.
            time.sleep(args.interval_minutes * 60)
    except KeyboardInterrupt:
        print("스케줄러를 종료합니다.")


# 이 파일을 직접 실행하면 main 함수를 호출한다.
if __name__ == "__main__":
    main()
