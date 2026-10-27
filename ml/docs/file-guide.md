# ML 폴더·파일 안내

Python 코드를 추가·수정하거나 설명하기 전에는 `python-writing-guide.md`의 사용자 이해 수준과 작성 기준을 확인한다.

재학습 스케줄러 초안의 실행 방법과 함수 역할은 `retrain-scheduler.md`에서 확인한다.
현재는 Uvicorn 서버를 실행하면 스케줄러도 함께 시작하며, 서버 종료 시 함께 정리한다.

## 학습과 직접 시험

| 파일 | 역할 |
| --- | --- |
| `data/expense/training/training_data.csv` | 정답이 있는 학습 CSV 전체. 두 모델이 배우는 유일한 기본 학습 CSV |
| `data/expense/training/training_data_simple.csv` | 품목·서비스 중심의 비교용 데이터. train_expense.py --simple로 선택 |
| `data/expense/testing/test_data.csv` | 직접 확인할 새 설명. 직접 입력 시험 시 자동 누적하며 학습에는 사용하지 않음 |
| `scripts/train_expense.py` | 기본 CSV와 추가 CSV(있을 때)를 합쳐 LogisticRegression·LinearSVC를 학습 |
| `scripts/test_expense.py` | 직접 입력 또는 시험 CSV로 두 모델의 결과·점수를 화면에 출력 |

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

`AD_SPONSORSHIP`은 광고로 **받은 수입**, `AD_PROMOTION`은 광고를 위해 **쓴 지출**이다. 미분류는 억지로 정답을 붙여 학습하는 카테고리가 아니다. 수입과 지출 학습 자료는 각 data/income, data/expense 폴더에서 관리한다.

## 읽는 순서

1. `training_data.csv`: 모델이 배우는 설명과 정답
2. `train_expense.py`: 두 모델 학습
3. `data/expense/testing/test_data.csv`와 `test_expense.py`: 새 설명으로 직접 시험
4. `app/main.py`: PDF 거래와 모델 연결

기존 train/test 분할 파일과 분할 스크립트는 제거했다.

## 선택형 SetFit 시험

- `train_expense.py --setfit`: 사전학습 언어 모델 기반의 별도 시험 모델 생성
- `test_expense.py --setfit`: 기존 두 모델과 SetFit 예측을 함께 확인
- `models/expense_setfit/`: SetFit 시험 모델. 서버에서 사용하지 않음
- `models/.hf_cache/`: 최초 모델 다운로드 캐시

실행 방법과 제한은 home-handoff.md의 SetFit 항목을 확인한다.

## 단순화 모델 선택

- `--simple`: 학습에서는 training_data_simple.csv, 시험에서는 단순화 모델을 선택
- `models/expense_classifier_simple/`: 단순화 CSV로 학습한 LogisticRegression·LinearSVC
- `models/expense_setfit_simple/`: 단순화 CSV로 학습한 SetFit
- 기본 명령과 서비스 모델 경로는 유지한다.

### 합친 데이터 비교용 파일

- `data/expense/training/training_data_combined.csv`: 기존·단순화 원본을 합치고 중복을 제거한 학습 자료. `--combined` 학습 때 다시 생성한다.
- `models/expense_classifier_combined/`: 합친 데이터로 학습한 LogisticRegression·LinearSVC와 학습 기록.
- `models/expense_setfit_combined/`: 합친 데이터로 학습한 SetFit과 학습 기록.
- 학습·시험 스크립트에 `--combined`를 붙여 선택한다. 직접 수정할 학습 자료는 두 원본 CSV다.

- `app/expense_classifier.py`: 최고 점수와 1·2위 차이로 미분류를 판정한다. 서버와 직접 시험이 공유한다.

## 수입 분류 구조 (TJK-190)

| 파일 | 역할 |
| --- | --- |
| `data/income/training/training_data.csv` | PDF에서 추출한 수입 설명·정답 학습 자료 |
| `data/income/testing/test_data.csv` | 직접 시험한 수입 설명과 확인할 정답 기록 |
| `scripts/train_income.py` | 수입 CSV를 읽어 전처리·모델 학습·저장. `--setfit`이면 SetFit 학습 |
| `scripts/test_income.py` | 저장한 수입 모델로 설명을 시험하고 점수·미분류 사유 표시 |
| `app/income_classifier.py` | 수입 모델 점수와 격차로 분류·미분류 판단 |
| `models/income_classifier/` | 수입 LogisticRegression·LinearSVC와 학습 기록 저장 위치 |
| `models/income_setfit/` | 수입 SetFit과 학습 기록 저장 위치 |

수입 코드는 `YOUTUBE_SETTLEMENT`(유튜브 정산), `AD_SPONSORSHIP`(광고·협찬),
`EXTERNAL_SUPPORT`(외부 후원), `OTHER`(기타)를 사용한다. `UNCLASSIFIED`는 학습 정답에 넣지 않는다.
두 가상 PDF의 수입 49건에서 같은 설명을 제거한 7개를 추가했다. 초기 자료는 정산 2개·광고/협찬 3개·외부 후원 2개다. 이후 사용자가 확인한 예시 14개를 보강해 현재 총 21개다.
거래 설명은 원문을 유지하고 거래처·날짜·금액은 학습 열에 넣지 않았다. 검토 상태는 PENDING이다.
수입 21개로 LogisticRegression·LinearSVC·SetFit 학습과 저장 모델 재로딩을 확인했다. 성능 평가는 별도다. 심플 데이터는 나중에 별도로 작성한다. 기타에는 영상 소스·굿즈 판매·강의료 예시를 포함한다. 최소 두 카테고리의 자료가 있어야 학습할 수 있다.
모델은 설명만 입력받으며 sample_id·group_id·review_status는 학습 자료 관리용이다.
추가 자료는 `data/income/user_approved/`가 있을 때 합치며 현재 지출 구조와 같은 APPROVED 제한이 있다.
사용자 데이터 자동 수집과 기존 CSV에 누적하는 연결은 아직 구현하지 않았다.
미분류 기준은 지출에서 가져온 임시값이므로 수입 자료를 시험한 뒤 다시 정해야 한다.
현재 PDF 서버의 수입 처리는 바꾸지 않았다. 데이터 추가·학습·시험 후 연결한다.

데이터를 추가한 다음 프로젝트 루트에서 실행한다.

```powershell
python ml/scripts/train_income.py
python ml/scripts/train_income.py --setfit
python ml/scripts/test_income.py --setfit
```

재학습에는 학습 명령 끝에 `--overwrite`를 붙인다. 시험은 `--setfit`을 빼면 머신러닝 두 모델만 비교한다.

- `data/expense/reference/categories.md`: 지출 정답 코드·한글 이름·포함 예시와 미분류의 차이를 확인하는 안내.
- `data/income/reference/categories.md`: 수입 정답 코드·한글 이름·포함 예시와 미분류의 차이를 확인하는 안내.
