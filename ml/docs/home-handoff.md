# ML 작업 상태와 실행 방법

추가된 CSV 누적 재학습 실행 구조는 [재학습 안내](retraining.md)를 참고한다.
수동 실행·외부 스케줄러용 명령을 제공하며 사용자 후보 수집 API와 운영 모델 자동 교체는 아직 연결하지 않았다.

정리일: 2026-10-23. 이번 범위는 Python 지출 모델 학습·시험과 기존 PDF 응답 연결이다. Spring·프론트·DB는 수정하지 않는다.

## 현재 구성

- `scripts/generate_dummy.py`: 기존 지출 후보 데이터 생성 코드. 이번에 수정하지 않았다.
- `scripts/split_expense_data.py`: v4 지출 후보 365건을 train 292건·test 73건으로 분할한다. 별도 validation은 만들지 않는다.
- `scripts/train_expense.py`: 기존 TF-IDF Pipeline을 사용해 LogisticRegression과 LinearSVC를 같은 train/test로 비교한다.
- `app/pdf_parser.py`: 기존 PDF·OCR 거래 추출 담당. 이번에 수정하지 않았다.
- `app/main.py`: 서버 시작 시 선택 모델을 읽고, 추출된 지출 거래의 설명만 모델에 전달한 뒤 같은 거래의 `category`에 결과를 넣는다.

## 데이터와 평가 기준

- 원본: `data/expense/category_dummy_v4_draft/labeled_candidates.csv`.
- 학습: 같은 폴더의 `train_test/train.csv`.
- 시험: 같은 폴더의 `train_test/test.csv`.
- 같은 group_id는 학습·시험에 나눠 넣지 않는다. 서로 다른 group_id의 유사한 의미까지 완전히 분리한 것은 아니다.
- 데이터는 합성 후보이며 `review_status=PENDING` 상태를 그대로 유지한다. 검수 완료나 실제 사용자 데이터로 표시하지 않는다.
- `review_cases.csv` 8건과 `needs_info.csv` 10건은 학습 정답으로 넣지 않는다.
- 두 모델의 시험 macro F1, 다음으로 정확도를 비교해 API용 모델 하나를 선택한다.
- 같은 시험 자료로 비교·선택한 점수이며 별도의 독립적인 최종 성능 평가 점수는 아니다. 시험 결과를 보고 설정을 반복 조정하지 않는다.

## 이번 실행 결과

- LogisticRegression: 시험 정확도 약 0.973, macro F1 약 0.973.
- LinearSVC: 시험 정확도 약 0.959, macro F1 약 0.960.
- 선택 모델: LogisticRegression.
- 이전 v2 자료·expense_baseline_v1 실험은 보존한다. 다른 데이터·분할이므로 이전 점수와 단순 비교하지 않는다.

## 실행 순서

프로젝트 최상위 PowerShell 기준이다.

```powershell
.\ml\.venv\Scripts\Activate.ps1
python ml/scripts/split_expense_data.py
python ml/scripts/train_expense.py
```

이미 분할·모델 파일이 있으면 실수로 덮어쓰지 않도록 중단한다. 실제로 다시 만들 때만 해당 명령에 `--overwrite`를 붙인다.

Git 제외 데이터가 없는 PC에서는 먼저 아래 생성 명령을 실행한다. 기존 검토 데이터를 덮어쓰지 않는다.

```powershell
python ml/scripts/generate_dummy.py --expanded-draft
```

모델 준비 후 Python 서버를 실행한다.

```powershell
cd ml
python -m uvicorn app.main:app --reload
```

- `http://127.0.0.1:8000/docs`의 `POST /api/pdf/analyze`에서 PDF를 선택해 실행한다.
- 기존처럼 Spring에서 `file`과 `ocrPages`를 함께 보내는 요청도 받는다.
- 서버 시작 시 모델을 한 번 읽는다. 모델을 다시 학습했다면 서버를 재시작한다.
- 모델이 없으면 시작 시 학습 명령을 안내하는 오류가 난다. 설치된 패키지를 추가·변경하지 않았다.

## 응답과 미분류 범위

- 응답 전체 형식은 기존 PDF 응답을 유지한다. 지출 거래의 `category`만 영문 카테고리 코드로 채운다.
- 모델 입력은 `description`뿐이다. 날짜·수입/지출 구분·금액·원문·추출 상태는 학습 특징에 넣지 않고 응답에서 유지한다.
- 수입과 구분 미확인 거래에는 이번 지출 모델을 적용하지 않는다. 현재 파서의 카테고리 값을 유지한다.
- 설명이 없는 지출은 `UNCLASSIFIED`로 반환한다.
- 점수가 낮은 예측을 미분류로 돌리는 임계값은 아직 정하지 않았다. 설명이 있는 모호한 거래도 현재 모델은 한 카테고리를 예측하므로, 모든 예측을 확정 정답으로 취급하지 않는다.
- 모델을 못 읽거나 예측에 실패하면 성공으로 꾸미지 않고 오류로 처리한다.
- 기존 차감 금액/새 차감 세율 PDF 형식 차이는 이번 ML 연결에서 변경하지 않았다.

## 생성 파일

- `models/expense_classifier/logistic_regression_bundle.pkl`: 전처리와 로지스틱 회귀 모델.
- `models/expense_classifier/linear_svc_bundle.pkl`: 전처리와 선형 SVM 모델.
- `models/expense_classifier/test_report.json`: 두 모델 시험 점수와 선택 모델 이름.
- `models/expense_classifier/test_predictions.csv`: 시험 설명·정답·두 모델 예측값 비교.

데이터·모델 파일은 Git 제외 대상이다. 생성·학습 코드와 안내 문서는 공유하고 다른 PC에서 같은 순서로 재생성한다.

## 다음 작업

- 실제 PDF의 추출 결과와 카테고리를 함께 확인한다.
- 모호한 설명의 미분류 판단 기준은 사용자와 정한 뒤 반영한다.
- 수입 데이터·모델은 지출 작업 이후 별도로 진행한다.
- Spring 저장·조회와 사용자 수정 데이터 재학습·스케줄러는 이번 범위에서 제외한다.
