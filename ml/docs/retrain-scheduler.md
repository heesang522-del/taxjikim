# LinearSVC 재학습 스케줄러

`scripts/retrain_scheduler.py`는 기존 학습 스크립트로 수입·지출 LinearSVC를 재학습한다. 외부 스케줄러 라이브러리는 사용하지 않는다.

## 실행 흐름

1. 수입 기본 CSV와 추가 CSV의 해시를 이전 성공 기록과 비교한다.
2. 첫 실행·자료 변경·결과 파일 누락이면 `train_income.py`를 실행한다.
3. 학습이 끝나면 해시·한국 시간·소요 시간을 기록하고 서버에 성공을 알린다.
4. 지출도 같은 방식으로 `train_expense.py`를 실행한다.
5. 작업 완료 후 기본 60분을 기다리고 반복한다.

같은 자료이고 `linear_svc_bundle.pkl`, `model_report.json`이 있으면 학습을 생략한다.
한 종류가 실패해도 다른 종류는 확인한다. 실패한 종류의 성공 기록은 갱신하지 않아 다음 주기에 재시도한다.
모델 다운로드나 별도 언어 모델 학습은 없다.

## 자료와 결과

읽는 자료는 `data/{income,expense}/training/training_data.csv`와 각 `user_approved/*.csv`다. 시험 CSV는 읽지 않는다.

- `models/retraining/income/classifier/`: 수입 LinearSVC 번들과 보고서
- `models/retraining/expense/classifier/`: 지출 LinearSVC 번들과 보고서
- `models/retraining/{income,expense}_last_training.json`: 마지막 성공 기록

위 결과는 Git에서 제외한다. 기본 서비스 모델 폴더는 덮어쓰지 않는다.

## 서버 실행과 결과 적용

프로젝트 루트에서 가상환경 활성화 후:

```powershell
python -m uvicorn ml.app.main:app --reload
```

서버 시작 시 기본 모델을 읽고 별도 스레드에서 스케줄러를 실행한다. 기존 성공 기록이 있으면 시작 시에도 재학습 결과를 확인한다.
성공 기록과 보고서의 자료 해시가 일치하고 번들이 정상 로딩되면 해당 종류의 LinearSVC를 서버 메모리에 반영한다. 실패하면 기존 모델을 유지한다.
서버 종료 신호가 오면 새 학습을 시작하지 않는다. 진행 중인 학습은 끝날 때까지 기다린다.
스케줄러를 여러 서버 작업자 또는 별도 터미널에서 동시에 실행하는 구성은 지원하지 않는다.

스케줄러만 한 번 확인하려면:

```powershell
python ml/scripts/retrain_scheduler.py --once
```

반복 간격은 `--interval-minutes`로 지정한다. 60분은 현재 기본값이며 운영 간격은 별도 결정한다.
