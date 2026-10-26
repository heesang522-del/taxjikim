# 학습과 재학습 — 같은 파일로 실행

`train_expense.py` 하나가 처음 학습과 추가 자료 재학습을 모두 담당한다.

1. `data/expense/training/training_data.csv` 전체를 읽는다.
2. `data/expense/user_approved/`에 추가 CSV가 있으면 합친다.
3. 추가 폴더가 없거나 비어 있으면 기본 자료만 학습한다.
4. 설명·ID 중복은 한 번만 사용하고 정답 충돌은 오류로 중단한다.
5. LogisticRegression과 LinearSVC를 모두 학습한다.

## 실행

프로젝트 최상위에서 가상환경 활성화 후:

```powershell
python ml/scripts/train_expense.py --overwrite
```

추가 자료 유무에 따라 다른 명령을 사용할 필요 없다.
기본 학습 CSV에 직접 행을 추가해도 같은 명령으로 다시 학습하면 된다.
두 모델 학습이 모두 성공한 뒤 모델 파일을 반영한다. 학습 도중 오류가 나면 기존 모델은 유지한다.
같은 출력 폴더의 동시 학습은 잠금 파일로 막는다.
매번 실행하면 전체를 다시 학습하며, 동일 입력 자동 생략과 실행별 이력 폴더는 더 이상 사용하지 않는다.

현재 서비스 모델을 바꾸지 않고 별도 폴더에 생성하려면:

```powershell
python ml/scripts/train_expense.py --output-dir ml/models/expense_candidate
python ml/scripts/test_expense.py --model-dir ml/models/expense_candidate
```

해당 결과 폴더가 이미 있으면 다시 만들 때 `--overwrite`를 추가한다.
서버는 모델을 한 번 읽으므로 서비스 모델을 재학습한 뒤에는 서버를 재시작한다.

## 추가 CSV

```csv
sample_id,description,category_code,group_id,review_status
user_batch01_001,영상 자막 편집 외주 작업비,OUTSOURCING,user_editing_group01,APPROVED
```

추가 CSV는 기존 구현처럼 APPROVED만 받는다. 자동 수집·승인 방식은 보류 중이다.
기본 합성 자료는 PENDING을 유지한다. 미분류는 정답으로 넣지 않는다.
추가 CSV는 날짜·배치별로 누적한다. 파일을 지우면 다음 학습에서 해당 자료가 빠진다.
기본 자료와 추가 자료가 같은 ID인데 내용이 다르거나 정답이 충돌하면 오류로 중단한다.
추가 자료 폴더는 Git에서 제외한다.

`data/expense/testing/test_data.csv`와 직접 입력한 설명은 학습에 포함하지 않는다.
시험 결과를 보고 모델을 자동 선택하지는 않는다. 선택 모델은 명령의 `--selected-model`로 지정한다.
미분류 임계값·사용자 확정값 수집·스케줄러 등록은 별도 후속 작업이다.
