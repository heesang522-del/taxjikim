# 재학습 스케줄러 초안

`scripts/retrain_scheduler.py`만 새 실행 파일로 추가했다. 기존 학습·서버 코드는 바꾸지 않는다.
외부 스케줄러 라이브러리는 사용하지 않는다.

## 실행 흐름

1. 수입 CSV의 내용이 지난 성공 때와 같은지 확인한다.
2. 처음 실행하거나 내용이 바뀌었으면 `train_income.py`를 실행한다.
3. 지출도 같은 방식으로 `train_expense.py`를 실행한다.
4. 성공한 데이터 식별값·한국 시간·걸린 시간을 기록한다.
5. 60분 기다린 뒤 반복한다. 내용이 같으면 학습을 건너뛴다.

수입 실패와 관계없이 지출도 확인한다. 실패한 종류는 다음 주기에 다시 시도한다.
첫 실행에는 기준 기록이 없으므로 수입·지출 모두 학습한다.

## 읽는 자료

- `data/income/training/training_data.csv`와 `data/income/user_approved/*.csv`
- `data/expense/training/training_data.csv`와 `data/expense/user_approved/*.csv`

추가 CSV는 기존 학습 코드의 APPROVED 형식을 그대로 따른다. 사용자 데이터 수집은 별도 작업이다.
시험 CSV와 지출 simple·combined 자료는 이번 초안에서 읽지 않는다.

## 결과

- `models/retraining/income/classifier/`: 수입 머신러닝 두 모델
- `models/retraining/expense/classifier/`: 지출 머신러닝 두 모델
- `models/retraining/*_last_training.json`: 종류별 마지막 성공 기록
- `models/retraining/scheduler.lock`: 중복 실행 방지용 임시 파일

결과는 별도 폴더에 저장한다. 서비스 모델을 교체하거나 서버를 재시작하지 않는다.
현재는 LogisticRegression·LinearSVC만 학습한다. SetFit 자동 학습·백업·자동 교체는 후속 작업이다.
실패 원인은 터미널에 출력하고 성공 기록은 갱신하지 않는다. 별도 실패 로그 파일은 아직 없다.
강제 종료로 잠금 파일이 남으면 실제 실행 중인지 확인한 후에만 제거한다.

## 실행 방법

프로젝트 루트에서 가상환경 활성화 후 한 번 확인:

```powershell
python ml/scripts/retrain_scheduler.py --once
```

60분 간격으로 반복:

```powershell
python ml/scripts/retrain_scheduler.py
```

10분 간격으로 반복:

```powershell
python ml/scripts/retrain_scheduler.py --interval-minutes 10
```

종료는 Ctrl+C다. 첫 확인은 실행 즉시 한다. 터미널을 닫거나 PC를 끄면 멈춘다.
Windows 작업 스케줄러 등록과 부팅 시 자동 실행은 하지 않았다.
브라우저 기능은 이번 변경에 없으며, 실행·생략·실패 메시지를 터미널에서 확인한다.

## 먼저 읽을 함수

- `get_data_files()`: 읽을 CSV 위치 모으기
- `get_data_versions()`: CSV 내용 비교용 값 만들기
- `run_training()`: 기존 학습 명령 실행
- `check_and_train()`: 변경 확인부터 학습·기록까지 한 종류 처리
- `main()`: 수입·지출을 차례대로 처리하고 기다리기

`subprocess.run()`은 지금 가상환경의 Python으로 학습 프로그램을 따로 실행한다.
`check=True`가 있으므로 학습이 실패하면 성공으로 기록하지 않는다.
`time.sleep()`은 지정한 초 동안 기다린다. 동시에 두 학습을 실행하지 않고 차례로 처리한다.
