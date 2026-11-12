# ML 파일 안내

수입·지출 분류기는 LinearSVC 하나만 사용한다. 경로는 `ml/` 기준이다.

| 경로 | 역할 |
| --- | --- |
| `app/main.py` | FastAPI, PDF 분석과 수입·지출 분류 연결, 재학습 결과 적용 |
| `app/pdf_parser.py` | 표·좌표·CSV 텍스트·OCR에서 거래 추출 |
| `app/ocr_parser.py`, `app/merge_parser.py` | OCR 단어와 행·셀 복원 |
| `app/income_classifier.py` | 수입 LinearSVC 예측과 미분류 기준 |
| `app/expense_classifier.py` | 지출 LinearSVC 예측과 미분류 기준 |
| `scripts/train_income.py`, `scripts/train_expense.py` | TF-IDF + LinearSVC 학습과 저장 검증 |
| `scripts/test_income.py`, `scripts/test_expense.py` | 직접 입력·시험 CSV 분류 |
| `scripts/feedback_preprocessing.py` | 추가 자료의 중복·승인·충돌 처리 |
| `scripts/retrain_scheduler.py` | 학습 자료 변경 확인과 자동 재학습 |
| `models/income_classifier/`, `models/expense_classifier/` | 공유하는 기본 서비스 모델과 학습 보고서 |
| `models/retraining/{income,expense}/classifier/` | 자동 재학습한 LinearSVC 번들과 보고서 |
| `models/retraining/*_last_training.json` | 마지막으로 성공한 재학습 기록 |
| `models/expense_classifier/previous_test_*` | 과거 LinearSVC 분할 시험 기록. 현재 모델 점수가 아님 |
| `requirements.txt` | 서버·PDF 파서·LinearSVC의 실행 의존성과 고정 버전 |

## 데이터

- `data/{income,expense}/training/training_data.csv`: 합성 기본 학습 자료
- `data/{income,expense}/testing/test_data.csv`: 직접 시험 자료. 학습에서 제외
- `data/{income,expense}/user_approved/*.csv`: 추가 학습 후보. Git 제외
- `data/{income,expense}/reference/`: 카테고리 설명과 검토 자료
- 지출의 `training_data_simple.csv`, `training_data_combined.csv`: 자료 구성 비교용. 기본 서비스와 분리

입력 설명 외의 날짜·금액·연도는 분류 모델의 피처에 넣지 않는다. 원래 거래 값은 PDF 응답에서 유지한다.
모델 번들의 `pipeline`에 TF-IDF와 LinearSVC가 함께 들어 있어 학습·예측 전처리를 공유한다.
