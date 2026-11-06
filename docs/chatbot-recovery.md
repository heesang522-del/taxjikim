# 챗봇 복구 결과 (2026-11-06)

## 복구 기준

- 작업 브랜치: `codex/recover-tjk-211-streaming`
- develop 기준: `069b1dd7cc0fda11564c7ef2f2fe22d26db32bad`
- 챗봇 원본: `e8a326d` (`pr/tjk-211,-chatbot-`), 스트리밍 기능 커밋 `ecfff19` 참고.
- 기존 feature, fix, PR 브랜치는 변경하지 않았다. 원격 push, PR 생성, DB 쓰기는 하지 않았다.
- 원본 커밋 전체를 merge/cherry-pick하지 않고 챗봇 전용 파일과 필요한 공통 연결만 반영했다.
- taxupload, Python, 공통 SSE Registry와 시뮬레이션 코드는 develop 기준으로 유지한다.

## 변경 파일 범위

- `back/src/main/java/com/kh/back/chatbot/`: 기존 챗봇·SSE·스트리밍·대화방 10개 제한 복구.
- `back/src/main/resources/mappers/chatbot/`, `prompts/Chatbot-System.st`: 저장 SQL과 시스템 안내 복구.
- `back/build.gradle`, `common/config/SecurityConfig.java`, `application.yml`: AI 의존성, 챗봇 인증 경로와 환경변수 설정만 추가.
- `front/src/pages/ChatbotPage.jsx`, `front/src/api/chatbotApi.js`: 기존 화면과 스트리밍 수신 복구.
- `front/src/App.jsx`, `utils/routes.js`, `styles/global.css`: 챗봇 경로·메뉴·스타일만 추가.
- `front/docs/api-draft.md`, `model-card.md`: 실제 복구한 API와 동작 기록.
- `back/src/test/java/com/kh/back/chatbot/ChatbotRecoveryTest.java`: 6개 회귀 테스트. 기존 test 폴더 ignore 규칙 때문에 이 테스트 파일만 Git 검토 대상으로 별도 등록했다.

## 확인 결과

- 백엔드 `compileJava`: 통과. 제한된 실행 환경의 Gradle native DLL 로딩 실패는 실행 권한 조정 후 해소했다.
- 백엔드 회귀 테스트 6개: 통과. 대화방 한도, 생성 잠금 순서, 타인 대화방 차단, delta/done 순서와 요청 식별자, 실패 이벤트, 분할 개인정보 마스킹을 검증했다.
- 프론트 `npm run build`: 통과. 기존 use no memo 지시문과 번들 크기 경고는 남아 있다.
- 챗봇 화면·API 대상 oxlint: 오류 0, 원본 화면 effect 내 상태 갱신 경고 1개.
- 실제 프론트 API 코드를 이용한 EventSource 대체 검사: 중간 출력, 다른 요청·대화방 이벤트 제외, 중복 제출 방지, 완료·실패·연결 오류·화면 이탈 정리 통과.
- 서버를 18081 포트에서 키 없이 기동하고 대화 목록·사용량·SSE 구독에 비로그인 GET 요청: 모두 401 확인 후 테스트 서버 종료.
- 현재 실행 설정의 DB에서 information_schema만 읽어 chatbot_conversations, chatbot_messages, chatbot_daily_usage와 users.USER_ID 컬럼 존재 확인. Mapper에서 사용하는 revision, deleted_at, processing_status 등 확인. DB 데이터나 구조는 변경하지 않았다.
- 실제 OpenAI API 호출, 로그인 계정으로 브라우저에서 대화 생성·삭제, DB를 포함한 동시 생성 검증은 미실행이다. 단위 테스트의 DB·AI는 대체 객체를 사용했다.

## 브라우저 확인 방법

1. 백엔드 실행 환경에 `OPENAI_API_KEY`와 계정에서 사용 가능한 `OPENAI_CHAT_MODEL`을 설정한다. 모델 미지정 기본 문자열은 복구 원본 값을 유지했으므로 실제 모델 사용 가능 여부를 확인해야 한다.
2. 백엔드를 기본 포트 8080에서 실행한다. 프론트는 `npm run dev`로 실행한다.
3. 로그인 후 `/chatbot`에 접근하고 새 대화를 만든다.
4. 질문 전송 후 네트워크 SSE에서 chatbot-ready → 여러 chatbot-delta → chatbot-done 순서를 확인한다. 완료 전에 화면에 답변이 늘어나야 한다.
5. 새로고침해 저장된 기록을 확인하고 대화 삭제, 11번째 활성 대화 생성 거절, 연결 오류 시 자동 질문 재전송이 없는지 확인한다.
6. 서버·프록시 운영 환경의 응답 버퍼링과 실행 환경의 모델 권한은 실제 호출에서 별도 확인한다.
