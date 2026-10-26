# 전체 초기 CSV와 추가 CSV로 재학습하기

## 입력과 결과

- 기본 학습: `data/expense/training/training_data.csv` 전체 365건
- 추가 자료: `data/expense/user_approved/*.csv`
- 결과: `models/expense_retraining/실행시각/dataset/`, `model/`, `run_manifest.json`
- 직접 시험: `data/expense/testing/test_data.csv`는 재학습에서 읽지 않는다.

기존 73건도 기본 학습에 포함한다. 기존 시험 분할을 계속 남겨두거나 새로 분할하지 않는다.
매번 기본 자료와 추가 CSV 전체를 합쳐 두 모델을 처음부터 학습한다.
추가 CSV가 없어도 실행 가능하며 동일 입력으로 성공한 기록이 있으면 생략한다.

## 추가 CSV 형식

```csv
sample_id,description,category_code,group_id,review_status
user_batch01_001,영상 자막 편집 외주 작업비,OUTSOURCING,user_editing_group01,APPROVED
```

기본 합성 자료는 PENDING을 유지한다. 추가 CSV는 기존 구현대로 APPROVED만 받는다.
APPROVED는 사용자 카테고리 지정과 다르다. 누가 승인할지와 수집 연결은 보류 중이다.
이 명령은 사용자 정보를 수집하거나 승인하지 않는다. 실제 사용자 자료는 Git 제외 폴더에 보관한다.
ID와 설명 중복은 한 번만 사용하고 정답 충돌·내용이 바뀐 ID·필수값 누락·미승인 추가 행은 거절한다.
기존 카테고리만 지원하며 미분류를 정답으로 넣지 않는다.
같은 문장 틀·유사 거래는 같은 group_id를 사용한다. 의미가 비슷한 설명까지 자동으로 찾지는 않는다.
CSV는 날짜·배치별 파일로 누적하며, 파일을 삭제하면 다음 학습에서 빠진다.
작성 중인 파일은 .tmp로 저장하고 완성 후 .csv로 이름을 바꾼다.

## 실행과 직접 확인

프로젝트 루트에서 가상환경 활성화 후:

```powershell
python ml/scripts/retrain_expense.py
```

완료 로그의 모델 경로를 사용해 직접 시험한다.

```powershell
python ml/scripts/test_expense.py --model-dir "완료 로그에 나온 실행 폴더/model" --description "물품 구매"
```

자동 시험 정확도나 자동 모델 선택은 제공하지 않는다. 두 모델을 직접 비교한다.
기존 서비스 모델은 덮어쓰지 않는다. 서비스 교체와 서버 재시작은 별도 작업이다.
동시 실행은 .retrain.lock으로 차단하고 실패 시 기존 모델을 유지한다.
실패 기록은 FAILED로 남고 잠금은 해제된다. 강제 종료 후 잠금이 남으면 실행 프로세스가 없는지 확인해야 한다.

## 후속 범위

사용자 확정값 수집 API·승인 방식·미분류 임계값·모델 교체·스케줄러 자동 등록은 아직 구현하지 않았다.
외부 스케줄러는 가상환경 Python으로 이 스크립트를 호출할 수 있지만 실행 주기는 미정이다.
