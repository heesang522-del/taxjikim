# ML 작업 상태와 실행 방법

정리일: 2026-10-26

## 현재 방식

- `data/expense/training/training_data.csv` 365건 모두 학습한다.
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
시험 입력은 자동 저장·학습되지 않는다. 학습 CSV에 이미 있는 문장은 표시한다.
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

설명이 없는 지출만 현재 UNCLASSIFIED로 반환한다. 점수 기반 기준은 아직 적용하지 않았다.
이전 292건 학습 모델로 검토한 0.25 기준은 전체 365건 모델에서 다시 확인해야 한다.
미분류 거래는 DB에서 조회하며 별도 미분류 누적 파일은 만들지 않는다.
사용자 수정값 수집·승인 자동화·모델 자동 교체·스케줄러는 후속 작업이다.
추가 CSV 재학습은 [retraining.md](retraining.md)를 참고한다.
