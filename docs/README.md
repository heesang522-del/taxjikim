# 문서 안내

최신 합의는 담당 문서 한 곳에서 관리한다. 과거 작업 기록을 현재 요구사항으로 사용하지 않는다.

## 세금·ML 읽는 순서

집에서 TJK-168을 이어갈 때는 [계산·집계 작업 인계](tax-implementation-handoff.md)를 먼저 읽는다. 학원에서 ML을 이어갈 때는 아래 ML 작업 상태를 먼저 확인한다.

| 순서 | 문서 | 역할 |
| --- | --- | --- |
| 1 | [예상세액 계산 기준](creator-tax-notes.md) | 확정한 입력·공제·기부·감면·평균 예측, 공식 출처와 법규 확인 |
| 2 | [전체 연결 명세](creator-tax-dummy-format.md) | 입력→분류→사용자 수정→거래·학습 후보 저장, 카테고리·API·검증 예시 |
| 3 | [ML 파일 안내](../ml/docs/file-guide.md) | 폴더·파일 역할과 영문 정답 코드의 한글 의미 |
| 4 | [ML 작업 상태](../ml/docs/home-handoff.md) | 데이터·모델 버전과 다음 단계 |

설치·실행은 [최상위 README](../README.md), 예시 금융 수치는 [확인 목록](../front/docs/to-verify.md)을 참고한다.
설계 기록과 실제 구현·실행 검증 완료는 구분한다.

## 연결·인증·협업 자료

- [백엔드 API](api-draft.md), [프론트 API 기록](../front/docs/api-draft.md), [화면 기록](../front/docs/model-card.md): 작성 시점의 구현 설명. 세금 목표 설계는 위 담당 문서를 따른다.
- [인증·인가 역할](authentication-authorization-study.md): 사용자 정보·소유권 검사.
- [백엔드 안내](backend-manual.md), [학습 노트](backend-review-guide.md): 인증·회원 코드 설명. 학습 노트의 과거 실패 정책은 현재 안내와 구분한다.
- 관리자·로그인 실패 문서와 [PR 25 초안](pr-25-review-draft.md): 해당 기능·PR의 검토 기록. 세금 계산 명세가 아니다.
- [코드 규칙](code-convention.md), [Git 규칙](git-rule.md), [PR 가이드](pr-guide.md): 공통 작업 규칙.

중복 대화 인계서는 삭제하고 필요한 합의·예시·출처를 담당 문서에 통합했다.
