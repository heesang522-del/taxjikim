# 카테고리 변경과 재학습

React는 분석별 변경 목록을 Spring의 `POST /api/v1/tax/inputs/{taxInputId}/update`로 보낸다. 요청 본문의 `changes` 배열에서 각 항목은 `transactionId`와 `category`를 담는다. Spring은 접근 권한을 확인하고 목록을 수정한 뒤 커밋 전에 실제 변경된 거래의 번호, 수입·지출 구분, 설명, 최종 카테고리를 내부 FastAPI `POST /api/training-feedback`로 전송한다. 이 경로는 신뢰된 내부 네트워크에만 노출한다.

전송에 실패하면 Spring은 DB 변경을 롤백하고 `TRAINING_FEEDBACK_FAILED` 오류를 반환한다. FastAPI는 유형별 `user_approved/category_feedback.csv`에 거래번호를 기준으로 최종 값을 반영한다. 기존 스케줄러가 다음 확인 시점에 이 파일을 읽는다. 기존 학습 코드의 중복과 정답 충돌 검사가 적용된다.
