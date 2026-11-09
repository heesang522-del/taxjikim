# ML 작업 상태와 실행 방법

수입·지출 거래 설명 분류는 TF-IDF + LinearSVC로 확정했다. 서버, 학습, 직접 시험, 자동 재학습 모두 같은 모델을 사용한다.

## 다른 PC에서 실행

프로젝트 루트의 PowerShell에서 실행한다. 현재 공유 모델을 학습한 환경은 Python 3.14.7이며 패키지는 `ml/requirements.txt` 버전을 따른다.

```powershell
py -3.14 -m venv ml/.venv
.\ml\.venv\Scripts\python.exe -m pip install -r ml/requirements.txt
.\ml\.venv\Scripts\python.exe -m uvicorn ml.app.main:app --reload
```

`http://127.0.0.1:8000/docs`에서 API를 확인한다. `GET /health`는 서버 상태 확인용이다.
서버는 PDF·OCR에서 거래를 추출한 뒤 수입·지출 설명을 각각 분류한다. DB 저장은 Spring에서 처리한다.

## 공유하는 서비스 모델

- `models/income_classifier/linear_svc_bundle.pkl`: 수입 분류 파이프라인
- `models/expense_classifier/linear_svc_bundle.pkl`: 지출 분류 파이프라인
- 각 폴더의 `model_report.json`: 학습 자료·버전·건수·선택 모델 기록

기본 학습 자료와 두 서비스 모델은 Git으로 공유한다. 가상환경, 사용자 추가 자료, 재학습 결과는 공유하지 않는다.
이미 공유 모델이 있으면 서버 시작 전에 직접 학습할 필요는 없다. 서버의 별도 스레드에서 재학습 자료를 확인한다.

## 학습

```powershell
.\ml\.venv\Scripts\python.exe ml/scripts/train_income.py --overwrite
.\ml\.venv\Scripts\python.exe ml/scripts/train_expense.py --overwrite
```

기본 학습 CSV 전체와 `user_approved` 폴더의 추가 CSV를 읽는다. 추가 자료는 기존 중복·정답 충돌·승인 상태 처리 기준을 유지한다.
학습 결과는 LinearSVC 번들 하나와 보고서다. 별도 학습/시험 분할이나 시험 점수를 통한 모델 자동 선택은 하지 않는다.
서비스 모델을 직접 다시 학습했다면 서버를 재시작한다. 자동 재학습 결과 반영은 [재학습 스케줄러](retrain-scheduler.md)를 따른다.

## 직접 시험

```powershell
.\ml\.venv\Scripts\python.exe ml/scripts/test_income.py
.\ml\.venv\Scripts\python.exe ml/scripts/test_expense.py
.\ml\.venv\Scripts\python.exe ml/scripts/test_expense.py --csv ml/data/expense/testing/test_data.csv
```

`--description "거래 설명"`으로 한 문장만 시험할 수 있다. 직접 입력과 `--description` 입력은 해당 종류의 시험 CSV에 저장하며 학습에는 사용하지 않는다. `--csv`는 시험 CSV를 읽기만 한다.
지출의 `--simple`, `--combined`는 기존 자료 구성 비교용이며 분류기는 모두 LinearSVC다. 기본 서비스 폴더와 분리해서 저장한다.

## 분류 결과와 한계

입력은 거래 설명 문자열, 출력은 카테고리·예측 카테고리·결정 점수·1위와 2위의 차이·미분류 사유다.
최고 결정 점수가 0.10 미만이거나 1·2위 차이가 0.75 미만이면 `UNCLASSIFIED`로 반환한다. 빈 설명도 미분류다.
기존 LinearSVC 기준을 유지했으며 점수는 확률이나 정확도가 아니다. 수입의 기준은 지출에서 가져온 임시값으로, 수입 자료를 사용한 별도 검증이 필요하다.
합성 기본 자료의 PENDING 상태를 유지한다. 모델의 분류가 실제 업무 경비 인정 여부를 보장하지 않는다.

`expense_classifier/previous_test_*`는 예전 292/73 분할 실험 중 LinearSVC 기록만 남긴 것이다. 현재 전체 학습 모델의 정확도 자료로 사용하지 않는다.
