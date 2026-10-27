"""수입·지출 CSV가 바뀌었을 때 기존 학습 프로그램을 주기적으로 실행한다."""


import argparse
import hashlib
import json
import subprocess
import sys
import time
from datetime import datetime
from pathlib import Path
from threading import Event
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

        # 파일 내용의 식별값(해시)을 만들어 지난 학습 자료와 비교한다.
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


# 머신러닝 두 모델과 SetFit을 차례로 학습하고 종료를 기다린다.
def run_training(transaction_type: str, output_dir: Path) -> None:
    script = BASE_DIR / "scripts" / f"train_{transaction_type}.py"
    # 실행할 명령: python train_income.py(또는 train_expense.py) --output-dir 저장폴더 --overwrite
    command = [
        sys.executable,   # 현재 가상환경의 Python 실행 파일
        str(script),      # 실행할 학습 파일 경로
        "--output-dir",   # 모델 저장 폴더를 지정하는 옵션
        str(output_dir),  # 위 옵션에 전달할 실제 저장 폴더 경로
        "--overwrite",    # 해당 폴더의 기존 모델을 새 학습 결과로 교체
    ]

    # 기존 학습 명령을 실행하고 실패하면 예외로 처리한다.
    subprocess.run(command, cwd=BASE_DIR.parent, check=True)

    # 실행할 명령: python 학습파일 --setfit --output-dir SetFit저장폴더 --overwrite
    setfit_dir = output_dir.parent / "setfit"
    setfit_command = [
        sys.executable,  # 현재 가상환경의 Python 실행 파일
        str(script),     # 앞에서 사용한 동일한 학습 파일
        "--setfit",      # 머신러닝 대신 SetFit을 학습하는 옵션
        "--output-dir",  # SetFit 모델의 저장 폴더를 지정하는 옵션
        str(setfit_dir), # 머신러닝 모델과 구분한 저장 폴더
        "--overwrite",   # 기존 SetFit을 새 학습 결과로 교체
    ]

    # 머신러닝 학습이 끝난 뒤 SetFit을 학습하고, 실패하면 예외로 처리한다.
    subprocess.run(setfit_command, cwd=BASE_DIR.parent, check=True)


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

        # SetFit 결과도 남아 있어야 전체 학습을 생략한다.
        setfit_dir = output_dir.parent / "setfit"
        for name in ["config_setfit.json", "model_head.pkl", "model.safetensors", "model_report.json"]:
            if not (setfit_dir / name).is_file():
                model_files_exist = False

        # 자료가 같고 모델도 있으면 재학습을 생략한다.
        if last_training.get("data_versions") == versions and model_files_exist:
            print(f"[{transaction_type}] 데이터 변경 없음: 학습 생략", flush=True)
            return True

        print(f"[{transaction_type}] 데이터 변경 또는 첫 실행: 학습 시작", flush=True)
        started = time.monotonic()
        run_training(transaction_type, output_dir)

        # 시작할 때 확인한 해시로 성공을 기록하고, 이후 변경은 다음 주기에 확인한다.
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


# 서버 종료 신호를 받을 때까지 수입·지출 학습을 60분 간격으로 확인한다.
def run_scheduler(stop_event: Event) -> None:
    SAVE_DIR.mkdir(parents=True, exist_ok=True)

    while not stop_event.is_set():
        check_and_train("income")

        # 수입 학습 중 서버가 종료됐다면 지출 학습을 새로 시작하지 않는다.
        if stop_event.is_set():
            break

        check_and_train("expense")

        # 60분을 기다리되, 서버 종료 신호가 오면 기다리기를 끝낸다.
        if stop_event.wait(CHECK_INTERVAL_MINUTES * 60):
            break


# 터미널에서 한 번 확인하거나 지정한 간격으로 반복한다.
def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--once", action="store_true", help="한 번만 확인하고 종료")
    parser.add_argument("--interval-minutes", type=int, default=CHECK_INTERVAL_MINUTES,
                        help="확인 간격(분)")
    args = parser.parse_args()
    # 확인 간격이 1분 미만이면 실행하지 않는다. (혹시 모를 에러 방지)
    if args.interval_minutes < 1:
        parser.error("확인 간격은 1분 이상이어야 합니다.")

    # 학습 결과와 기록을 저장할 폴더를 준비한다.
    SAVE_DIR.mkdir(parents=True, exist_ok=True)
    try:
        # 터미널에서 스케줄러를 따로 실행할 때 사용하는 반복 처리다.
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
