# ML 폴더·파일 안내

실행 방법과 현재 상태는 [ML 작업 안내](home-handoff.md)를 확인한다.

## 폴더 역할

| 폴더 | 역할 |
| --- | --- |
| `app/` | PDF 거래 추출과 지출 카테고리 분류 API |
| `scripts/` | 더미데이터 생성·train/test 분할·모델 학습 코드 |
| `data/expense/category_dummy_v4_draft/` | 현재 사용하는 지출 설명·정답 후보 365건과 별도 보류 자료 |
| `data/expense/category_dummy_v4_draft/train_test/` | 학습·시험 자료와 분할 요약 |
| `models/expense_classifier/` | 두 모델·시험 결과·서버에서 선택해 사용할 모델 정보 |
| `models/expense_baseline_v1/` | 이전 실험 결과 보존본 |
| `docs/` | 파일 역할과 실행 안내 |

## 파일 역할

| 파일 | 역할 |
| --- | --- |
| `app/main.py` | 서버 시작 시 모델을 읽고 기존 PDF 응답의 지출 거래에 예측 카테고리를 추가 |
| `app/pdf_parser.py` | PDF·OCR에서 날짜·구분·설명·금액 등 거래 정보를 추출 |
| `app/__init__.py` | app을 Python 패키지로 구성 |
| `scripts/generate_dummy.py` | 설명·정답 카테고리 후보 생성 |
| `scripts/split_expense_data.py` | 같은 그룹을 함께 유지하며 train/test 분할 |
| `scripts/train_expense.py` | 설명 전처리 Pipeline과 두 분류 모델을 학습·시험·저장 |
| `data/expense/category_dummy_v4_draft/labeled_candidates.csv` | 학습·시험에 사용할 설명·정답 후보 |
| `data/expense/category_dummy_v4_draft/review_cases.csv` | 카테고리 기준이 미확정인 경계 사례. 이번 학습에서 제외 |
| `data/expense/category_dummy_v4_draft/needs_info.csv` | 설명이 부족한 사례. 이번 학습에서 제외 |
| `data/expense/category_dummy_v4_draft/manifest.json` | 원본 데이터의 출처·건수·검토 상태 |
| `data/expense/category_dummy_v4_draft/train_test/train.csv` | 전처리와 모델을 학습할 설명·정답 |
| `data/expense/category_dummy_v4_draft/train_test/test.csv` | 학습에 넣지 않고 두 모델을 비교할 설명·정답 |
| `data/expense/category_dummy_v4_draft/train_test/split_manifest.json` | 분할 비율·시드·원본 식별값·분할 건수 |
| `models/expense_classifier/logistic_regression_bundle.pkl` | TF-IDF와 로지스틱 회귀 모델을 함께 저장 |
| `models/expense_classifier/linear_svc_bundle.pkl` | TF-IDF와 선형 SVM 모델을 함께 저장 |
| `models/expense_classifier/test_report.json` | 정확도·macro F1·카테고리별 결과와 API 선택 모델 |
| `models/expense_classifier/test_predictions.csv` | 시험 자료의 설명·정답·두 모델 예측을 한 행씩 비교 |

## 정답 코드와 카테고리

CSV의 `category_code`는 아래 한글 카테고리를 뜻한다. 내부 데이터는 영문 코드를 유지하고, 이 표로 의미를 확인한다.

| 구분 | 정답 코드 | 한글 카테고리·포함 범위 |
| --- | --- | --- |
| 수입 | `YOUTUBE_SETTLEMENT` | 유튜브 플랫폼 정산 — 플랫폼 안의 후원 포함 |
| 수입 | `AD_SPONSORSHIP` | 광고·협찬 수입 |
| 수입 | `EXTERNAL_SUPPORT` | 유튜브 밖의 외부 후원 수입 |
| 지출 | `SOFTWARE_LICENSE` | 소프트웨어 — 편집 프로그램·음원 라이선스·게임 이용권 등 |
| 지출 | `EQUIPMENT` | 하드웨어 — 카메라·마이크·리뷰용 전자기기 등 |
| 지출 | `TELECOMMUNICATION` | 통신비 — 휴대전화·인터넷·데이터 요금 등 |
| 지출 | `TRANSPORT` | 교통비 — 택시·버스·기차·주차요금 등 |
| 지출 | `SUPPLIES_PROPS` | 소모품·소품 — 색지·테이프·장식물·촬영용 음식과 재료 등 |
| 지출 | `SPACE_RENTAL` | 공간 대여 — 스튜디오·녹음 부스·촬영 출장 숙박 등 |
| 지출 | `OUTSOURCING` | 외주비 — 편집·번역·디자인 등 외부 작업 의뢰 비용 |
| 지출 | `AD_PROMOTION` | 광고·홍보 지출 — 내 채널·영상을 알리기 위해 지불한 비용 |
| 공통 | `OTHER` | 기타 — 용도는 알지만 위 범주에 포함되지 않는 거래 |
| 공통 | `UNCLASSIFIED` | 미분류 — 설명이 부족하거나 판단을 보류한 거래 |

`AD_SPONSORSHIP`은 광고로 **받은 수입**, `AD_PROMOTION`은 광고를 위해 **쓴 지출**이다. 미분류는 억지로 정답을 붙여 학습하는 카테고리가 아니다. 현재 생성 CSV에는 지출 후보만 있으며 수입 후보는 아직 추가하지 않았다.

## 읽는 순서

1. `data/expense/category_dummy_v4_draft/labeled_candidates.csv`: 설명과 정답 후보 확인
2. `scripts/split_expense_data.py`: 학습·시험 분할 방식 확인
3. `scripts/train_expense.py`: 전처리·두 모델 학습·시험 방식 확인
4. `models/expense_classifier/test_predictions.csv`: 시험 설명과 두 모델의 예측 비교
5. `app/main.py`: 선택한 모델을 PDF 거래에 연결하는 코드 확인

기존 버전과 이번 결과는 데이터·분할이 다르므로 점수를 단순 비교하지 않는다. 별도 validation 자료는 사용하지 않는다.
