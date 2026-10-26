# ML 폴더·파일 안내

## 학습과 직접 시험

| 파일 | 역할 |
| --- | --- |
| `data/expense/training/training_data.csv` | 정답이 있는 365건 전체. 두 모델이 배우는 유일한 기본 학습 CSV |
| `data/expense/testing/test_data.csv` | 직접 확인할 새 설명. 학습에는 사용하지 않음 |
| `scripts/train_expense.py` | 전체 학습 CSV로 LogisticRegression·LinearSVC를 학습 |
| `scripts/test_expense.py` | 직접 입력 또는 시험 CSV로 두 모델의 결과·점수를 화면에 출력 |
| `scripts/retrain_expense.py` | 전체 기본 자료와 승인된 추가 CSV로 새 모델 생성 |

## 서버와 결과 파일

| 파일 | 역할 |
| --- | --- |
| `app/main.py` | PDF 거래 설명을 선택한 모델로 분류 |
| `app/pdf_parser.py` | PDF·OCR에서 거래 정보를 추출 |
| `app/__init__.py` | Python 패키지 표시 |
| `requirements.txt` | 실행에 필요한 라이브러리 목록 |
| `models/expense_classifier/*_bundle.pkl` | 설명 전처리와 모델 두 개 |
| `models/expense_classifier/model_report.json` | 학습 건수·원본 식별값·서버 선택 모델. 현재 시험 정확도는 담지 않음 |
| `models/expense_classifier/previous_test_report.json` | 이전 292/73 분할 실험의 점수. 현재 전체 학습 모델의 점수가 아님 |
| `models/expense_classifier/previous_test_predictions.csv` | 이전 분할 실험의 개별 예측 기록 |
| `data/expense/reference/manifest.json` | 기본 데이터의 출처·건수·검토 상태 |
| `data/expense/reference/needs_info.csv` | 설명이 부족한 더미 사례. 학습 제외 |
| `data/expense/reference/review_cases.csv` | 정답 기준이 미확정인 더미 사례. 학습 제외 |
| `docs/home-handoff.md` | 현재 실행 방법 |
| `docs/retraining.md` | 추가 CSV 재학습 방법 |

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

1. `training_data.csv`: 모델이 배우는 설명과 정답
2. `train_expense.py`: 두 모델 학습
3. `data/expense/testing/test_data.csv`와 `test_expense.py`: 새 설명으로 직접 시험
4. `app/main.py`: PDF 거래와 모델 연결

기존 train/test 분할 파일과 분할 스크립트는 제거했다.
