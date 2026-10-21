# ML 폴더·파일 안내

집에서 이어갈 때는 [작업 인계서](home-handoff.md)를 먼저 읽는다.

설치·실행 방법은 [최상위 README](../../README.md), 전체 설계는 [상세 명세](../../docs/creator-tax-dummy-format.md)를 참고한다. Python 코드 내부 설명은 각 `.py` 파일의 주석에 작성한다.

## 폴더 역할

| 폴더 | 역할 |
| --- | --- |
| `app/` | FastAPI 서버 코드 |
| `scripts/` | 데이터 생성 등 준비 작업 코드 |
| `data/` | 생성한 데이터와 검토 자료 |
| `data/expense/` | 지출 분류용 데이터. v1·v2는 기존 보존본, v3는 추가 설명을 반영해 생성할 자료 |
| `data/expense/category_dummy_v4_draft/` | 자체 점검한 확장 초안. 기존 설명 180개와 새 설명 185개를 모은 후보이며 아직 학습용으로 분할하지 않음 |
| `models/expense_baseline_v2/` | 추가 설명으로 다시 학습할 지출 모델 비교 결과 |
| `docs/` | ML 작업 안내 문서 |
| `.venv/` | 프로젝트용 Python과 설치 라이브러리 |
| `__pycache__/` | Python 실행 시 생성되는 캐시 |

## 파일 역할

| 파일 | 역할 |
| --- | --- |
| `app/main.py` | 서버 시작점. 현재 `/health`로 서버 상태 확인 |
| `app/__init__.py` | `app`을 Python 패키지로 구성 |
| `scripts/generate_dummy.py` | 설명·정답 카테고리로 검토용 더미데이터 생성 |
| `data/expense/category_dummy_v4_draft/labeled_candidates.csv` | 접미사 변형 없이 설명 365개와 정답 후보를 담은 확장 자료 |
| `data/expense/category_dummy_v4_draft/review_cases.csv` | 정답을 확정하지 않은 범주 경계·정보 부족 사례와 보류 이유 |
| `data/expense/category_dummy_v4_draft/needs_info.csv` | 명확히 정보가 부족한 설명 10개 |
| `data/expense/category_dummy_v4_draft/manifest.json` | 확장 자료의 출처·건수·상태 요약 |
| `scripts/split_expense_data.py` | 비슷한 거래 의미를 묶어 지출 데이터를 학습·검증·시험용으로 분할 |
| `scripts/train_expense.py` | TF-IDF와 두 분류 모델을 학습하고 검증 자료로 비교 |
| `data/expense/category_dummy_v3/labeled_candidates.csv` | 기존 지출과 여행·먹방·게임·일상·리뷰 설명의 정답 검토 자료 |
| `data/expense/category_dummy_v3/needs_info.csv` | 정보가 부족한 설명의 미분류 검증 자료 |
| `data/expense/category_dummy_v3/manifest.json` | 데이터 생성 시점의 버전·출처·건수·상태 요약 |
| `data/expense/category_dummy_v3/splits/train.csv` | 모델이 학습할 설명과 정답 |
| `data/expense/category_dummy_v3/splits/validation.csv` | 모델 비교·미분류 기준 조정용 자료 |
| `data/expense/category_dummy_v3/splits/test.csv` | 기준 결정 후 최종 평가용 자료 |
| `data/expense/category_dummy_v3/splits/needs_info_validation.csv` | 정보 부족 설명의 미분류 기준 조정용 자료 |
| `data/expense/category_dummy_v3/splits/needs_info_test.csv` | 정보 부족 설명의 최종 미분류 평가용 자료 |
| `data/expense/category_dummy_v3/splits/split_manifest.json` | 분할 건수·기준·원본 식별 기록 |
| `models/expense_baseline_v2/logistic_regression_bundle.pkl` | TF-IDF와 로지스틱 회귀 모델을 함께 저장한 실험 파일 |
| `models/expense_baseline_v2/linear_svc_bundle.pkl` | TF-IDF와 선형 SVM 모델을 함께 저장한 실험 파일 |
| `models/expense_baseline_v2/*_validation.csv` | 검증 자료의 설명·정답·예측·정답 일치 여부 |
| `models/expense_baseline_v2/validation_report.json` | 두 모델의 검증 점수·카테고리별 성능·실험 조건 |
| `requirements.txt` | 라이브러리 설치 목록과 버전 |
| `docs/file-guide.md` | 폴더·파일 역할과 읽는 순서 |
| `docs/home-handoff.md` | 집에서 이어갈 현재 상태·실행 방법·다음 작업·유지할 합의 |

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

1. `data/expense/category_dummy_v3/labeled_candidates.csv`: 설명과 정답 카테고리 확인
2. `needs_info.csv`: 분류하기 어려운 설명 확인
3. `manifest.json`: 생성 데이터의 전체 구성 확인
4. `scripts/generate_dummy.py`: 데이터 생성 방식 확인
5. `app/main.py`: API 서버 코드 확인

기존 v2 자료로 첫 실험을 마쳤다. 현재 코드는 대표 설명 180개로 v3 자료와 새 분할·모델 결과를 생성하도록 준비했으며, 아직 재생성·재학습하지 않았다. 최종 시험·미분류 처리·예측 API 연결은 아직 하지 않았다. 현재 모델은 실험용이다. 파일이 추가되거나 역할이 바뀌면 이 안내서도 갱신한다.

확장 초안 v4는 별도로 생성·자체 점검했다. 현재 학습·분할 코드는 계속 v3를 사용하며 v4를 자동으로 학습하지 않는다. v4도 모델 입력은 description 하나이고 카테고리는 생성 정답 후보다.
