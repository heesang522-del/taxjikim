# API 초안

모든 함수는 `src/api/*.js`의 async 함수이며, 현재는 `src/mocks/*.json`을 300~800ms 지연 후 반환한다. 화면은 `useApi(함수)` 훅으로만 호출한다.

| 함수 | 파일 | 사용 화면 | 응답 mock |
| --- | --- | --- | --- |
| `getHomeData()` | `api/home.js` | 홈 | `mocks/home.json` |
| `getProfile()` | `api/profile.js` | 마이페이지 | `mocks/profile.json` |
| `getCalcOptions()` | `api/calculator.js` | 세금 계산 | `mocks/calculator.json` |
| `getCommunity()` | `api/community.js` | 커뮤니티 | `mocks/community.json` |
| `getFaqs()` | `api/support.js` | 고객센터 | `mocks/support.json` |
| `getNotifications()` | `api/notifications.js` | 알림 모달 | `mocks/notifications.json` |
| `getAdminStats()` | `api/admin.js` | 관리자 | `mocks/admin.json` |

## getAdminStats() 응답 예시 (예시값)
~~~json
{
  "model": { "accuracy": 0.912, "recall": 0.874 },
  "data": { "transactionCount": 1284 },
  "ops": [{ "id": "members", "label": "총 회원수", "value": "34,250명", "tone": "default" }],
  "users": [{ "id": 1, "name": "baduser99 (홍*동)", "desc": "크리에이터 | 경고 3회 누적" }],
  "inquiry": { "id": 1042, "title": "[문의 #1042] ...", "status": "답변 대기", "meta": "작성자: freelancer01 | ..." }
}
~~~

## getCalcOptions() 응답
~~~json
{
  "regions": ["서울특별시"],
  "jobs": ["개발자 (소프트웨어)"],
  "categories": ["소프트웨어 개발비", "장비구입비"],
  "comparisonRows": [["부가가치세 신고", "해당 없음", "필요 (간이·일반과세자)"]],
  "guideLinks": [{ "id": 1, "label": "...", "message": "..." }]
}
~~~

## 로컬 상태 (API 아님)
- 거래 내역: `store/useTransactionStore.js` (zustand). 세액은 이 내역과 `utils/tax.js`로 화면에서 계산한다.
- 로그인 여부: `store/useAuthStore.js` (zustand persist). 실제 인증 없음.


## 로그인·로그아웃 실제 API 연결 (2026-10-14)

- 새 LoginForm과 Header 화면을 유지하며 실제 인증 API에 연결한다.
- 로그인: POST /api/v1/auth/sessions, loginId·password·rememberMe(false)를 전송한다.
- 성공 응답의 accessToken과 서버 회원 정보는 현재 페이지 메모리에만 보관한다. 세션 스토리지 저장·복원을 제거했으며 새로고침 시 GET /users/me로 기존 JWT 쿠키 인증 상태를 확인해 화면을 복원한다.
- 로그인 화면의 실패 횟수·잠금 판단은 서버 응답을 사용한다. 브라우저 mock 잠금 시간 안내는 적용하지 않는다.
- 로그아웃: DELETE /api/v1/auth/sessions/current를 기존 JWT 쿠키로 호출하고, 성공 후 화면의 인증 상태를 비운다.
- 회원가입·계정 찾기·마이페이지 등 다른 화면은 이번 작업에서 실제 API로 연결하지 않는다.
- 이름·닉네임이 로그인 응답에 없으므로 현재 화면용 사용자 표시에는 loginId를 사용한다.
- 모델 입력·출력·계산 로직은 변경하지 않는다.

## 인증·회원 화면 실제 연결 (2026-10-15)

- 회원가입: SignupForm → userApi의 중복 확인·이메일 발송/확인·가입 요청. mock 계정 저장과 브라우저 인증번호 생성은 사용하지 않는다.
- 요청 필드: loginId, password, name, nickname, email, 선택 birthDate. 프로필 선택 시 multipart의 profile 파일로 보낸다. 전화번호 입력은 제거했다.
- 인증번호 안내는 5분, 인증 완료 후 가입 안내는 10분이며, 최종 유효성은 서버가 판단한다. 이메일을 바꾸면 화면의 이전 인증 상태를 초기화한다.
- 계정 찾기: 이름·이메일·인증번호로 로그인 아이디를 조회하고 화면에서 마스킹한다.
- 비밀번호 재설정: 계정과 이메일 일치 및 메일 인증 후 서버에 새 비밀번호를 전송한다. 성공하면 기존 Redis 세션도 종료한다.
- 마이페이지 회원 정보: GET /users/me로 읽고 닉네임·인증한 이메일·비밀번호·프로필 이미지 변경을 연결했다. 저장 성공 후 서버 정보를 다시 조회해 스토어를 갱신한다.
- 탈퇴: 현재 비밀번호와 복구 불가 안내를 확인하고 DELETE /users/me를 요청한다. 성공 후 브라우저 인증 상태를 비운다. FK에 연결된 데이터가 있으면 서버 오류 안내를 표시하며 다른 테이블을 임의로 삭제하지 않는다.
- 모든 회원 요청은 api/userApi.js → 공통 client → /api/v1 경로를 사용한다. 인증이 필요한 요청은 브라우저의 기존 HttpOnly JWT 쿠키로 인증하며 프론트는 Bearer 헤더를 만들지 않는다.
- 대기 중 중복 요청을 막고 실패 메시지를 표시한다. 서버의 error.fields는 각 입력 필드에 연결한다.
- 계정 찾기·재설정 API 상세 경로와 JSON은 루트 docs/api-draft.md를 기준으로 한다.
- 실제 검증: 이메일 발송·인증, 생년월일 없이 가입, 로그인, 아이디 찾기, 비밀번호 재설정 및 기존 토큰 거부, 프로필 조회·닉네임/비밀번호/이미지 변경, 미인증 이메일 변경 거부, 인증 후 현재 이메일 저장, 임시 계정 탈퇴 및 이후 로그인 거부를 확인했다.
- 서로 다른 이메일 주소로 바꾸는 실제 발송 검증은 별도 필요하다. 이번 실제 이메일 저장 테스트는 같은 주소로 인증 후 저장하는 흐름을 확인했다.
- 세금 계산·게시글·관리자 등 다른 기능은 이번 연동 대상이 아니다.


## PDF 검토 거래 저장
- POST /api/v1/tax/reviewed-transactions (Bearer 인증 필수)
- 요청: fileId, itemIndex(추출 후보 배열의 0부터 시작하는 인덱스), taxYear, transactionType(INCOME/EXPENSE), transactionDate, category, description(최대 200자), amount(양수 정수).
- 서버 로그인 회원의 TAX_LEDGER/TAX_EVIDENCE 파일만 허용한다. userId는 서버 인증에서 얻는다.
- 회원·연도별 TAX_INPUTS에 연결해 TAX_TRANSACTIONS에 저장하고, 생성된 transactionId와 저장된 거래를 반환한다.
- (SOURCE_FILE_ID, SOURCE_ITEM_INDEX) 유일 제약 및 파일 행 잠금으로 중복 요청은 최초 거래를 반환한다. 수정 API가 아니므로 재시도 시 다른 값으로 덮어쓰지 않는다.
- 프론트는 성공 응답의 값으로 계산 내역을 추가한다. 오류 시 입력값을 보존한다. 비회원 업로드는 API를 호출하지 않는다.
- 목록 제거는 현재 계산에서만 제외한다. 서버 저장 거래 조회/수정/삭제 API와 새로고침 복원은 이번 범위에 포함하지 않는다.

### DB 선행 변경 (기존 데이터는 NULL 유지)
```sql
ALTER TABLE TAX_TRANSACTIONS
  ADD COLUMN SOURCE_FILE_ID BIGINT NULL,
  ADD COLUMN SOURCE_ITEM_INDEX INT NULL,
  ADD UNIQUE KEY UK_TAX_TRANSACTION_SOURCE (SOURCE_FILE_ID, SOURCE_ITEM_INDEX);
```
- 이미 적용한 DB에는 반복 실행하지 않는다. 기존 자동 파싱 거래는 두 필드를 NULL로 저장한다.


## PDF 월별 사용금액 표시
- evidence.items의 valueKind=MONTHLY_SUMMARY는 거래가 아닌 월별 집계이다.
- transactionMonth는 YYYY-MM, transactionDate는 null로 반환한다. 임의의 월초·월말 날짜를 만들지 않는다.
- 각 발급기관 행의 12개월 합계가 원본 합계와 일치할 때만 반환한다. 개인정보·기관명은 항목명에 포함하지 않는다.
- 화면은 집계/상세와 월별 금액을 분리한다. 0원인 월은 표에서 숨기고, 집계·월별 항목에는 거래 입력 폼을 표시하지 않는다.
- 양수이고 실제 거래일이 있는 DETAIL 항목에만 확인·저장 폼을 표시한다. 집계와 월별 사용금액은 계산 합계에 자동 반영하지 않는다.


## PDF 업로드 요청 중단

- `uploadTaxPdfApi(file, { accessToken, taxYear, signal })`은 선택적인 `AbortSignal`을 받아 Axios 요청에 전달한다.
- 요청 시간 제한은 파일당 120초이다. 중단·시간 초과는 화면에 표시할 오류로 반환하며 성공 응답 구조는 유지한다.
- 클라이언트 대기 중단은 서버 저장 취소 API가 아니다.


## 검토 거래 수정

- `PUT /api/v1/tax/reviewed-transactions`: 기존 POST와 같은 요청 필드. 본인 파일의 저장된 항목만 수정하며 기존 transactionId를 유지한다.
- POST는 최초 저장과 재시도용으로 최초 결과를 반환한다. 수정 요청에 기존 항목이 없으면 404이다.
- 계산 포함·검토 제외 상태는 현재 브라우저 계산 목록에서 관리하며 서버 거래의 삭제를 의미하지 않는다.


## PDF 응답 문서 종류 제거

- `documentKind` 필드와 백엔드 `TaxPdfDocumentKind` enum을 제거했다. 거래 목록과 검토 결과 데이터는 기존 필드를 사용한다. 별도 응답 파일은 기존 TaxTransactionDto 내부 PdfResult로 통합했다.


## 추출 후보 초기값

- 검토 항목의 선택 필드 `description`, `category`, `transactionType`, `transactionDate`는 실제 추출된 값이 있을 때 입력 초기값으로 사용한다.
- 누락 필드는 빈칸이며, 금액만 있는 후보도 업로드 직후 목록에 표시한다. 저장 전 후보는 계산에서 제외한다.
