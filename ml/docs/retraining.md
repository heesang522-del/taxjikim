# LinearSVC 학습과 재학습

수입은 `train_income.py`, 지출은 `train_expense.py`가 처음 학습과 추가 자료 재학습을 모두 담당한다.

1. 해당 종류의 기본 `training_data.csv` 전체를 읽는다.
2. `user_approved`에 추가 CSV가 있으면 기존 검토 기준에 따라 합친다.
3. 설명·ID 중복은 한 번만 사용하고 정답 충돌은 중단한다.
4. TF-IDF와 LinearSVC를 하나의 Pipeline으로 학습한다.
5. 저장 후 다시 읽은 모델의 예측이 일치하는지 확인하고 번들과 보고서를 반영한다.

프로젝트 루트에서 가상환경 활성화 후:

```powershell
python ml/scripts/train_income.py --overwrite
python ml/scripts/train_expense.py --overwrite
```

기본 서비스 결과는 `models/{income,expense}_classifier/`에 저장한다. 학습·저장 검증이 실패하면 기존 모델 파일은 반영하지 않는다.
매번 전체를 다시 학습하며, 변경 자료 자동 확인은 [스케줄러](retrain-scheduler.md)가 담당한다.
직접 학습 명령은 한 번에 하나씩 실행한다. 동시 실행용 잠금 파일은 사용하지 않는다.

서비스 모델을 바꾸지 않고 별도 결과를 만들려면:

```powershell
python ml/scripts/train_expense.py --output-dir ml/models/expense_candidate
python ml/scripts/test_expense.py --model-dir ml/models/expense_candidate
```

지출의 `--simple`과 `--combined`는 자료 구성 비교용 옵션이다. 각각 별도 모델 폴더를 사용하며, 분류기는 동일한 LinearSVC다.

## 추가 CSV

```csv
sample_id,description,category_code,group_id,review_status
user_batch01_001,영상 자막 편집 외주 작업비,OUTSOURCING,user_editing_group01,APPROVED
```

추가 자료는 기존 구현처럼 APPROVED 행을 대상으로 중복·정답 충돌 등을 처리한다. 기본 합성 자료는 PENDING을 유지한다.
추가 폴더가 없거나 비어 있으면 기본 자료만 학습한다. 시험 CSV와 직접 시험 입력은 학습에 넣지 않는다.
자료를 삭제하면 다음 학습에서 해당 자료가 빠지므로 임의로 삭제하지 않는다.

`model_report.json`의 선택 모델은 `linear_svc`로 고정한다. 같은 학습 자료를 다시 맞힌 점수를 시험 정확도로 기록하지 않으며, 별도 검증 없이 임계값을 바꾸지 않는다.
