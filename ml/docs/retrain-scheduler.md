# 재학습 스케줄러 초안

`scripts/retrain_scheduler.py`를 FastAPI 서버의 시작·종료 흐름에 연결했다. 기존 학습 코드는 그대로 사용한다.
외부 스케줄러 라이브러리는 사용하지 않는다.

## 실행 흐름

1. 수입 CSV의 내용이 지난 성공 때와 같은지 확인한다.
2. 처음 실행하거나 내용이 바뀌었으면 `train_income.py`로 머신러닝 두 모델을 학습한 뒤, `--setfit` 옵션으로 SetFit도 학습한다.
3. 지출도 같은 방식으로 `train_expense.py`를 실행한다.
4. 한 종류의 세 모델 학습이 모두 성공하면 데이터 식별값·한국 시간·전체 학습에 걸린 시간을 기록한다.
5. 60분 기다린 뒤 반복한다. 내용이 같으면 학습을 건너뛴다.

수입 실패와 관계없이 지출도 확인한다. 실패한 종류는 다음 주기에 다시 시도한다.
첫 실행에는 기준 기록이 없으므로 수입·지출 모두 학습한다.
학습이 성공하면 시작 시 확인한 해시로 성공을 기록한다. 학습 후 CSV가 바뀌었다고 실패 처리하지 않는다.
그 이후 변경은 다음 정기 확인 때 비교해서 재학습한다. 학습 입력을 별도 복사본으로 고정하는 기능은 아직 없다.
60분은 현재 확인용 기본 간격이며, 실제 운영 간격은 하루 또는 일주일 단위로 추후 정한다.

## 읽는 자료

- `data/income/training/training_data.csv`와 `data/income/user_approved/*.csv`
- `data/expense/training/training_data.csv`와 `data/expense/user_approved/*.csv`

추가 CSV는 기존 학습 코드의 APPROVED 형식을 그대로 따른다. 사용자 데이터 수집은 별도 작업이다.
시험 CSV와 지출 simple·combined 자료는 이번 초안에서 읽지 않는다.

## 결과

- `models/retraining/income/classifier/`: 수입 머신러닝 두 모델
- `models/retraining/expense/classifier/`: 지출 머신러닝 두 모델
- `models/retraining/income/setfit/`: 수입 SetFit
- `models/retraining/expense/setfit/`: 지출 SetFit
- `models/retraining/*_last_training.json`: 종류별 마지막 성공 기록

결과는 별도 폴더에 저장한다. 서비스 모델을 교체하거나 서버를 재시작하지 않는다.
LogisticRegression·LinearSVC·SetFit을 차례로 학습한다. 모델 백업·서비스 자동 교체는 후속 작업이다.
SetFit이 실패하면 전체 성공 기록은 갱신하지 않고 다음 주기에 다시 시도한다.
SetFit 결과가 없는 경우 CSV가 같아도 재학습한다. 서비스 모델은 별도 폴더이므로 계속 유지된다.
실패 원인은 터미널에 출력하고 성공 기록은 갱신하지 않는다. 별도 실패 로그 파일은 아직 없다.
스케줄러와 학습 명령은 한 번에 하나씩 실행하는 전제다. 스케줄러·수입/지출 학습의 잠금 파일은 사용하지 않는다.

## 서버와 함께 실행

프로젝트 루트에서 가상환경 활성화 후 다음 명령 하나로 서버와 스케줄러를 함께 시작한다.

```powershell
python -m uvicorn ml.app.main:app --host 0.0.0.0 --port 8000 --reload
```

`main.py`의 `lifespan()`이 기존 모델을 불러온 뒤 별도 스레드에서 `run_scheduler()`를 실행한다.
서버 시작 직후 수입·지출을 한 번 확인하고, 작업 완료 후 60분마다 다시 확인한다.
학습 자체는 기존처럼 별도 Python 프로그램으로 실행하므로 서버는 기존 모델로 요청을 처리한다.
서버 종료 시 Event로 종료 신호를 보내고 스케줄러 스레드가 끝날 때까지 기다린다.
학습 도중 종료하면 진행 중인 학습은 완료될 때까지 기다리고 다음 종류의 학습은 새로 시작하지 않는다.
`--reload`로 재시작할 때도 이전 서버의 스케줄러 종료 후 새 서버에서 시작한다.
여러 서버 작업자를 동시에 실행하는 구성은 이번 범위에 포함하지 않는다.
별도 스케줄러 터미널을 함께 켤 필요는 없다. 학습 결과의 서비스 자동 적용은 아직 없다.

## 스케줄러만 직접 확인

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

종료는 Ctrl+C다. 첫 확인은 실행 즉시 한다. 직접 실행 모드는 서버 실행과 별개로 확인할 때만 사용한다.
Windows 작업 스케줄러 등록과 부팅 시 자동 실행은 하지 않았다.
브라우저 기능은 이번 변경에 없으며, 실행·생략·실패 메시지를 터미널에서 확인한다.

## 먼저 읽을 함수

- `get_data_files()`: 읽을 CSV 위치 모으기
- `get_data_versions()`: CSV 내용 비교용 값 만들기
- `run_training()`: 기존 학습 명령 실행
- `check_and_train()`: 변경 확인부터 학습·기록까지 한 종류 처리
- `run_scheduler()`: 서버에서 실행할 반복 확인과 종료 신호 처리
- `main()`: 터미널에서 스케줄러만 실행할 때 옵션 처리

`subprocess.run()`은 지금 가상환경의 Python으로 학습 프로그램을 따로 실행한다.
`check=True`가 있으므로 학습이 실패하면 성공으로 기록하지 않는다.
`time.sleep()`은 지정한 초 동안 기다린다. 동시에 두 학습을 실행하지 않고 차례로 처리한다.

## 후속 검토 메모 (2026-10-27)

- [ ] 중복 확인의 시간·메모리 사용량: 현재 방식은 유지한다. 데이터가 커졌을 때 점검한다.
- [ ] `review_status`·`APPROVED` 조건: 사용자는 현재 데이터 흐름에서 사용하지 않는 것으로 이해하고 있다.
  학습 코드에는 아직 필수값·추가 CSV 승인 조건이 남아 있으므로, 관련 파일을 검토할 때 함께 정리한다.
  이 조건이 이미 제거됐다고 가정하지 않는다.
- [ ] 형식이 정상이고 충돌도 없지만 정답이 잘못된 사용자 자료의 처리 정책은 나중에 정한다.

현재 코드는 같은 설명의 정답 충돌 시 학습을 중단한다.
충돌 자료를 별도 CSV에 보관하고 제외한 뒤 계속 학습하는 방식은 논의한 변경안이며 아직 구현하지 않았다.
