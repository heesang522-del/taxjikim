# ML 작업 상태와 실행 방법

정리일: 2026-10-26

## 현재 방식

- `data/expense/training/training_data.csv` 전체를 학습한다.
- `data/expense/testing/test_data.csv`는 새 설명을 직접 넣어 확인하는 용도이며 학습에 포함하지 않는다.
- 두 모델 모두 학습하고, 서버는 기존 선택인 LogisticRegression을 유지한다.
- 새 시험 결과로 모델을 자동 선택한 것은 아니다. 이전 정확도 97.3%는 과거 292/73 분할 실험 기록이다.
- 별도 validation이나 자동 train/test 분할은 사용하지 않는다.
- 기본 더미데이터는 합성 후보이며 PENDING 상태를 유지한다. 사람 검수 완료 자료로 표시하지 않는다.

## 학습

프로젝트 최상위 PowerShell에서 가상환경 활성화 후:

```powershell
.\ml\.venv\Scripts\Activate.ps1
python ml/scripts/train_expense.py --overwrite
```

`--overwrite`는 기존 모델을 다시 만들 때만 사용한다. 새로운 PC는 `requirements.txt` 설치가 필요하다.
같은 명령이 재학습도 담당한다. `data/expense/user_approved/`에 추가 CSV가 있으면 합치고, 없으면 기본 학습 CSV만 사용한다.
기본 데이터·모델은 Git으로 공유하므로 이미 파일이 있으면 재학습 없이 서버 실행이 가능하다.

## 직접 시험

설명을 계속 입력하면서 두 모델의 카테고리·점수를 확인한다. 빈 입력으로 종료한다.

```powershell
python ml/scripts/test_expense.py
```

한 문장만 확인:

```powershell
python ml/scripts/test_expense.py --description "물품 구매"
```

`data/expense/testing/test_data.csv`의 `description` 열에 새 설명을 추가한 후 한꺼번에 확인:

```powershell
python ml/scripts/test_expense.py --csv ml/data/expense/testing/test_data.csv
```

`expected_category`는 직접 정답 코드를 메모하는 선택 열이다. 비워도 실행된다.
직접 입력과 `--description`으로 시험한 설명은 `data/expense/testing/test_data.csv`에 자동 저장한다.
같은 설명은 중복 저장하지 않으며 정답은 비워둔다. 학습에는 넣지 않는다.
`--csv`는 저장 없이 기존 시험 자료를 읽기만 한다. 학습 CSV에 이미 있는 문장은 표시한다.
학습하지 않은 설명을 사용해야 새로운 표현에 대한 결과를 확인할 수 있다.
모든 CSV·사용자 직접 입력은 지출 설명만 넣는다. 수입 판별이나 업무 경비 인정 여부를 판단하는 도구는 아니다.
LogisticRegression의 분류 점수와 LinearSVC의 결정 점수는 서로 다른 척도다.
점수는 실제 정답 확률을 보장하지 않으며 두 모델의 숫자 크기를 직접 비교하지 않는다.

## 서버

```powershell
cd ml
python -m uvicorn app.main:app --reload
```

모델 파일만 바뀌면 자동 반영되지 않으므로 학습 후 서버를 재시작한다.
`http://127.0.0.1:8000/docs`에서 PDF 분석 요청을 확인할 수 있다.
PDF·OCR에서 추출한 거래 중 지출의 설명만 모델에 전달하고 나머지 거래 정보는 유지한다.
수입 모델은 아직 연결하지 않았다. DB 저장은 Spring 담당이다.

## 미분류와 후속 작업

설명이 없거나 최고 점수·1위와 2위의 격차가 기준보다 낮은 지출은 UNCLASSIFIED로 반환한다. 기준은 아래 미분류 처리 항목을 따른다.
이전 292건 학습 모델로 검토한 0.25 기준은 전체 학습 자료 모델에서 다시 확인해야 한다.
미분류 거래는 DB에서 조회하며 별도 미분류 누적 파일은 만들지 않는다.
사용자 수정값 수집·승인 자동화·모델 자동 교체·스케줄러는 후속 작업이다.
추가 CSV 재학습은 [retraining.md](retraining.md)를 참고한다.

## 장비·게임 소프트웨어 설명 보충 (2026-10-26)

하드웨어 40건(저장장치·게임기·음향기기·촬영 주변장치), 소프트웨어 24건(플랫폼별 게임 구매·게임 이용 구독 등)을 추가했다.
추가 설명은 직접 작성한 합성 후보이며 PENDING 상태다. 사용자 검수 완료 자료는 아니다.
게임기 본체는 EQUIPMENT, 게임 소프트웨어 구매·게임 이용 구독은 SOFTWARE_LICENSE로 구분한다.
당시에는 게임 내 결제를 보류했으며, 이후 합의로 스팀 게임 내 재화·스킨·아이템 결제는 소프트웨어에 추가했다. 플랫폼 지갑 충전은 추가하지 않았다.
단순한 플랫폼명 결제만으로 구매 대상을 단정하지 않으며, 플랫폼 게임 구매처럼 대상이 드러난 설명을 사용했다.
`reference/manifest.json`은 초기 생성 기록으로 유지하고, 현재 학습 건수는 model_report.json과 실제 CSV에서 확인한다.

## 여행 이동 설명 보충 (2026-10-26)

교통비에 항공·배편·현지 이동 설명 30건을 추가했다. 국제선·수하물 운임·페리·카페리·수상교통·현지 열차·렌터카 등의 이동 비용이다.
모두 합성 후보(PENDING)이며, 경비 인정 여부를 판정하는 정답이 아니라 거래 종류를 분류하는 예시다. 관광·체험 입장권이나 항공사 지갑 충전은 포함하지 않았다.

## 짧은 설명·스팀 결제 기준 (2026-10-26)

문장이 아니어도 품목이 분명한 마이크·스피커·저장장치·게임기·이동요금 등의 짧은 설명을 추가했다.
사용자 합의에 따라 `스팀 결제`와 스팀 게임·게임 내 아이템/재화/스킨 결제는 SOFTWARE_LICENSE로 묶는다.
이는 서비스 내부 분류 기준이며 세법상 경비 인정 여부를 뜻하지 않는다. 스팀 하드웨어 기기인 스팀덱은 EQUIPMENT로 구분한다.
구체적인 구매 대상이 드러난 경우 해당 품목 기준을 사용하며, 스팀 이외 플랫폼의 모든 불명확한 결제까지 자동으로 동일 취급하지 않는다.
`물품 구매`, `정기 결제`처럼 대상이 전혀 없는 설명을 특정 카테고리 정답으로 추가하지는 않았다.
학습에 넣은 짧은 문장을 다시 확인한 점수는 새 설명에 대한 성능 평가가 아니다.

## SetFit 딥러닝 비교 시험

기존 TF-IDF 모델 두 개와 FastAPI 서비스는 유지한다. 선택 옵션으로 SetFit을 따로 학습·시험한다.
한국어를 포함한 다국어 Sentence Transformer `sentence-transformers/paraphrase-multilingual-MiniLM-L12-v2`를 사용한다.
첫 학습에는 공개 사전학습 모델 다운로드가 필요하다. 패키지는 갱신된 requirements.txt로 설치한다.

```powershell
python ml/scripts/train_expense.py --setfit
python ml/scripts/test_expense.py --setfit
```

SetFit 모델은 `models/expense_setfit/`, 내려받은 모델 캐시는 `models/.hf_cache/`에 저장한다. 두 폴더 모두 Git 제외 대상이다.
현재 기본 학습은 CPU에서 전체 1에포크를 진행한 후 분류기를 학습한다. 기존 527건 모델의 첫 시험은 20단계로 제한했었다.
학습을 완료해도 성능 개선이 보장되지는 않는다.
학습 횟수는 `--setfit-steps`로 지정하며 재학습할 때는 `--overwrite`를 붙인다.
입력 데이터·중복 처리는 기존 train_expense.py와 동일하다. 별도 validation은 추가하지 않았다.
`test_expense.py --setfit`은 기존 두 모델과 SetFit의 카테고리·점수를 나란히 출력한다.
모델 간 점수 크기를 정확도로 비교하지 않고 학습에 없는 새 설명의 정답 여부로 비교한다.
이 옵션은 서버 모델을 자동으로 SetFit으로 교체하지 않는다.

BERT 직접 분류는 언어 모델과 분류기를 함께 맞추는 방식이다.
고정 문장 임베딩 방식은 언어 모델을 그대로 두고 분류기만 학습한다.
SetFit은 문장 임베딩 모델을 작업에 맞게 학습하고 그 결과로 분류기를 학습한다.

### 첫 시험에서 확인한 점

527건을 사용한 20단계 제한 학습과 저장·로컬 재로딩, 기존 두 모델과 함께 결과 출력까지 확인했다.
마이크·지하철·배 등 짧은 품목·이동 표현에서 원하는 카테고리가 나오는 사례를 확인했다.
반면 거래 설명이 아닌 프로그램 실행 명령에도 소프트웨어 점수 약 0.997을 반환했다.
따라서 점수가 높아졌다는 이유로 정확도 개선이나 미분류 판정 가능성을 확정하지 않는다.
현재는 서버에 미연결된 비교용 모델이며, 별도 정답이 있는 새로운 설명과 무관한 입력을 함께 검토해야 한다.

## 단순 학습데이터 초안 (2026-10-26)

기존 `data/expense/training/training_data.csv`는 유지하고, 옆에 `training_data_simple.csv`를 추가했다.
새 초안은 9개 카테고리 296건으로, 여행·촬영·먹방·리뷰 같은 상황 조합을 빼고 품목·서비스를 중심으로 작성했다.
기기·프로그램·외주 작업처럼 카테고리를 구분하는 정보는 유지했다. 스팀 결제의 기존 소프트웨어 분류 합의도 유지했다.
정답 검수가 완료된 자료로 표시하지 않고 PENDING 상태를 사용한다.
기본 명령은 training_data.csv를 읽고, --simple을 붙이면 training_data_simple.csv를 읽는다. 단순화 모델은 별도 폴더에 저장하며 서비스 모델을 교체하지 않는다.
단순화가 더 좋은지는 같은 새로운 설명으로 비교한 뒤 결정한다.

## 단순화 데이터 선택해서 학습·시험

프로젝트 최상위에서 가상환경 활성화 후 실행한다.

```powershell
python ml/scripts/train_expense.py --simple
python ml/scripts/test_expense.py --simple
```

단순화 데이터로 SetFit도 학습해서 세 모델을 함께 보려면:

```powershell
python ml/scripts/train_expense.py --simple --setfit
python ml/scripts/test_expense.py --simple --setfit
```

결과 폴더가 이미 있으면 다시 학습하는 명령 끝에 `--overwrite`를 붙인다.
SetFit의 기본값 -1은 단계 제한 없이 num_epochs=(1, 1)에 따라 한 바퀴 학습한다는 뜻이다.
직접 단계 제한을 주고 싶을 때만 `--setfit-steps 양수`를 사용한다.
단순화 모델은 `models/expense_classifier_simple/`와 `models/expense_setfit_simple/`에 저장한다.
기존 모델은 유지하고, 기본 시험 명령과 FastAPI는 계속 기존 모델을 사용한다.
추가 승인 CSV가 있으면 기존과 동일하게 합치므로 순수 기본 자료 비교 때에는 추가 폴더 내용을 확인한다.

## 기존·단순화 데이터를 합친 모델

`--combined`는 두 원본 CSV를 매번 읽고 같은 설명의 중복을 제거해 학습한다.
정답 충돌은 자동 선택하지 않고 학습을 중단한다. `--simple`과 동시에 쓰지 않는다.
현재 527건 + 296건에서 중복 58건을 제외한 765건이다. 건수는 데이터 변경 시 달라진다.
`training_data_combined.csv`는 실제 합친 학습 자료를 확인하는 결과 파일이다.
데이터 추가는 원본 `training_data.csv` 또는 `training_data_simple.csv`에 한다. 합친 CSV는 재학습 때 다시 생성된다.

```powershell
python ml/scripts/train_expense.py --combined --overwrite
python ml/scripts/train_expense.py --combined --setfit --overwrite
python ml/scripts/test_expense.py --combined --setfit
```

머신러닝 두 모델은 `models/expense_classifier_combined/`, SetFit은 `models/expense_setfit_combined/`에 저장한다.
기존 모델과 단순화 모델, FastAPI 서비스 선택은 유지한다. 시험 입력은 계속 시험 CSV에만 저장한다.

## 미분류 처리 (2026-10-26)

`app/expense_classifier.py`에서 서버와 직접 시험의 판정을 함께 관리한다.
최고 점수 또는 1·2위 차이가 아래 기준보다 작으면 `UNCLASSIFIED`다. 경계값과 같으면 통과한다.

| 모델 | 최고 점수 하한 | 1·2위 차이 하한 |
| --- | ---: | ---: |
| LogisticRegression | 0.35 | 0.15 |
| LinearSVC | 0.10 | 0.75 |
| SetFit | 0.95 | 0.20 |

설명이 비어 있어도 미분류다. `OTHER`는 정답 카테고리이며 미분류와 구분한다.
시험 화면에는 최종 카테고리, 점수, 격차, 미분류된 경우 원래 예측과 사유를 표시한다.
PDF 응답에는 기존 category 값만 반영하며 수입·날짜·금액 등은 바꾸지 않는다.
미분류 설명을 별도 CSV에 수집하지 않는다. 직접 시험 입력의 기존 시험 CSV 저장은 유지한다.
사용자 수정값 승인·재학습은 이 작업 범위에 포함하지 않는다.

기준은 앞선 수동 시험을 바탕으로 한 임시 운영값이다. 기존·단순화·합친 모델에 같은 모델 종류별 기준을 적용한다.
데이터별 최적값으로 검증한 수치는 아니다. 높은 점수의 오분류까지 막는 것은 아니며, SetFit은 무관한 설명에도 높은 점수를 줄 수 있다.
서버 모델은 기존 LogisticRegression 선택을 유지한다. 서버가 켜져 있으면 변경 코드를 반영하도록 재시작한다.

```powershell
python ml/scripts/test_expense.py --combined --setfit
```

### 적용 후 점검 기록

기존 수동 점검 사례(정상 53개·무관한 입력 30개)를 합친 LinearSVC로 다시 실행했다.
정상 설명은 정답 43개·미분류 10개·오분류 0개, 무관한 입력은 미분류 28개·분류 2개였다.
기준 검토에 사용했던 사례를 다시 확인한 결과이며 독립 시험 정확도로 해석하지 않는다.
경계값·점수 부족·격차 부족·빈 설명, 세 모델 시험 출력, PDF 처리 함수와 분류 함수의 연결을 확인했다.
PDF 추출 결과를 대체한 함수 호출 점검으로, 실제 PDF 파일과 Spring·화면을 통한 전체 실행은 별도 확인이 필요하다.
