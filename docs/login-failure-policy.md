# 로그인 실패·차단 정책과 코드 리뷰 안내

작성일: 2026-10-16. 현재 구현을 기록한 검토용 문서이며 최종 확정안이 아니다. 기존 HttpOnly JWT 쿠키 인증은 유지한다.

## 현재 구현 정책 (검토 중)

| 누적 실패 | 결과 |
| --- | --- |
| 1~5회 | 실패 안내, 첫 잠금까지 남은 횟수(6 - 누적 실패) |
| 6회 | 30초 차단 |
| 7회 | 1분 차단 |
| 8회 | 3분 차단 |
| 9회 | 5분 차단, 다음 실패 시 관리자 해제 필요 안내 |
| 10회 | DB 상태 LOCKED, 관리자만 해제 |

- 차단 중 요청은 실패 횟수를 늘리거나 차단 종료 시각을 연장하지 않는다.
- 시간 경과로 풀리는 것은 일시 차단뿐이다. 누적 실패 기록은 남는다.
- 로그인 인증 성공 또는 관리자 해제 때 실패 기록을 삭제한다.
- 관리자 이용 정지 BANNED와 로그인 실패 잠금 LOCKED를 구분한다.
- 실패·차단 이메일은 보내지 않는다. 이전 이메일 토큰 해제 API는 HTTP 410으로 종료한다.

## 서버의 처리 순서

1. AuthServiceImpl.login이 입력을 확인하고 CustomAuthenticationProvider를 호출한다.
2. Provider의 별도 트랜잭션에서 회원 행을 SELECT FOR UPDATE로 조회한다.
3. 회원의 LOCKED/BANNED/DORMANT 등 상태를 확인한다.
4. Redis에서 현재 실패·차단 상태를 확인한다. 차단 중이면 비밀번호 검사나 실패 증가 없이 거부한다.
5. 비밀번호가 틀렸으면 Redis 스크립트에서 확인·증가·차단 시간 설정을 한 번에 실행한다.
6. 10회에 도달하면 ACTIVE 회원만 LOCKED로 변경한다.
7. 예상된 LoginFailureException이 발생해도 Provider의 트랜잭션은 커밋한다. 바깥 로그인 트랜잭션의 롤백과 독립적이다.
8. 상태와 비밀번호를 통과한 경우 실패 기록을 삭제하고 기존 로그인·쿠키 발급 흐름을 계속한다.

## 왜 Redis Lua를 사용하는가

키는 LOGIN_ATTEMPT:v2:아이디이고 count와 until 필드를 가진다. 같은 아이디의 대소문자·앞뒤 공백은 키에서 통일한다.

Java에서 확인 → 증가 → 저장을 나눠 실행하면 동시 실패가 차단을 건너뛰거나 시간을 덮어쓸 수 있다. Lua 스크립트는 Redis 안에서 이 과정을 다른 요청이 끼어들지 못하게 실행한다.

Redis TIME으로 종료 시각을 계산하며 TTL은 설정하지 않는다. 서버 재시작으로 실패 기록이 초기화되지 않는다. 단, Redis 자체 재시작·데이터 유실까지 보장하려면 Redis 영속성·메모리 정책을 별도로 설정해야 한다.

## 왜 별도 트랜잭션을 사용하는가

원래는 로그인 트랜잭션 안에서 DB 잠금 후 RuntimeException을 던져 잠금까지 취소될 수 있었다.

Provider에 REQUIRES_NEW와 noRollbackFor=LoginFailureException을 적용했다. 예상된 로그인 거절만 커밋하며 DB·Redis 장애 등 예상하지 못한 예외는 정상 롤백 대상이다. 모든 예외를 커밋하도록 바꾸지 않았다.

로그인과 관리자 해제는 같은 회원 행의 FOR UPDATE 잠금을 사용해 순서대로 처리한다. 관리자 해제는 DB 상태를 ACTIVE로 복구하고 기존 로그인 세션을 종료한 후 Redis 실패 기록을 삭제한다.

DB와 Redis는 하나의 분산 트랜잭션이 아니다. Redis 오류에는 관리자 해제를 성공으로 응답하지 않고 DB 변경을 롤백한다. 두 저장소 사이의 장애를 완전히 원자적으로 복구하는 시스템까지 추가한 것은 아니다.

## 화면과 API

- 로그인 실패의 error.details: failedAttempts, remainingAttempts, blockedUntil, serverTime, locked.
- 일시 차단은 HTTP 429 / LOGIN_TEMPORARILY_BLOCKED, 일반 실패는 HTTP 401 / LOGIN_FAILED, 최종 잠금은 HTTP 401 / LOCKED.
- 시간 값은 Asia/Seoul 오프셋을 포함한 ISO 8601로 보낸다.
- LoginForm은 blockedUntil - serverTime만큼의 남은 시간을 받아 화면에서만 카운트다운한다. 매초 API를 호출하지 않는다.
- 차단 여부는 로그인 버튼을 눌렀을 때 POST /api/v1/auth/sessions의 응답으로만 확인한다. 입력 중 자동 조회는 하지 않는다.
- 새로고침 후에도 로그인 버튼을 눌러 남은 차단 시간을 확인한다. 아이디·인증 정보를 브라우저 저장소에 새로 저장하지 않는다.
- 관리자가 해제한 뒤에는 로그인 버튼으로 다시 시도한다. 별도의 상태 확인 버튼은 두지 않는다.
- 관리자 회원·게시판 관리 탭의 '회원 관리'는 GET /api/v1/admin/account-locks와 DELETE /api/v1/admin/account-locks/{loginId}를 사용한다.
- 목록은 최근 가입 계정 순으로 최대 100개다. 기존 회원 관리 위치에서 실제 BANNED·LOCKED 계정만 표시한다.
- 관리자 API는 서버 Security에서 ADMIN만 허용한다. 프론트의 버튼 숨김만으로 권한을 보호하지 않는다.

## 파일별 역할

| 파일 | 역할 |
| --- | --- |
| LoginAttemptService / LoginAttemptServiceImpl | Redis 실패 기록·일시 차단 관리 |
| CustomAuthenticationProvider | 회원 상태·비밀번호·누적 실패를 종합해 인증 판단, DB 최종 잠금 |
| LoginAttemptDto / LoginFailureException | 화면에 전달할 실패 상태와 예상된 인증 거절 |
| UserSecurityMapper.java / XML | 인증 시 행 잠금, DB 잠금, 관리자 목록·해제 SQL |
| AccountLockService / AccountLockServiceImpl | 실제 관리자 해제와 실패 기록 초기화 |
| AdminAccountLockApiController / SecurityConfig | 관리자 전용 목록·해제 API와 권한 |
| ApiResponse / GlobalExceptionHandler | 기존 응답 형식을 유지하면서 error.details 제공 |
| AuthServiceImpl | 기존 이메일 해제 요청 거부, 불필요해진 이메일 의존성 제거 |
| JwtAuthenticationFilter | 로그인 상태 조회와 폐기된 이메일 API는 기존 세션과 무관하게 처리 |
| authApi.js / useAuthStore.js | 상태·관리자 API 호출, 실패 details를 화면까지 전달 |
| LoginForm.jsx | 서버 상태 조회, 카운트다운, 재시도 버튼 관리 |
| AccountLockManagement.jsx / AdminPage.jsx | 실제 잠금 목록·해제 영역 연결 |

UnlockEmailService와 LoginUnlockTokenService 및 구현체는 더 이상 사용하지 않아 제거했다. 일반 회원가입·계정 찾기 이메일 기능은 유지한다.

## 검증 범위와 직접 확인 순서

로컬 테스트는 프로젝트 back/.gitignore 규칙에 따라 Git에서 제외된다.

- LoginAttemptRedisTests: 실제 로컬 Redis의 무작위 테스트 키만 사용. 1~10회 정책, 차단 중 유지, TTL 없음, 동시 실패를 확인한다. 다음 단계 진행 테스트는 해당 테스트 키의 until만 과거로 바꿔 대기 시간을 생략한다.
- LoginPolicyTests: 비밀번호·회원 상태, 실패 증가, 성공 초기화, 관리자 해제, DB 장애를 확인한다.
- LoginLockTransactionTests: Spring 트랜잭션 프록시와 모의 JDBC 연결로 안쪽 커밋·바깥 롤백을 검증한다. 실제 MySQL의 데이터 변경 결과를 읽는 통합 테스트는 아니다.
- AdminAccountLockSecurityTests: 실제 Security 필터 체인으로 비회원 401, 일반 회원 403, 관리자 허용을 검증한다.

브라우저 확인:

1. 백엔드 재시작 후 테스트 회원으로 5회 실패한다. 다음 실패에 30초 차단되는지 확인한다.
2. 차단 중 00:30 카운트다운과 로그인 버튼 비활성화를 확인한다.
3. 새로고침하고 같은 아이디로 로그인 버튼을 눌러 남은 시간이 표시되는지 확인한다.
4. 차단 종료 후 실패를 반복해 1분 → 3분 → 5분으로 진행하는지 확인한다.
5. 5분 종료 후 실패하면 LOCKED 안내가 나타나는지 확인한다.
6. 관리자 로그인 후 실제 잠금 관리 영역에서 제한을 해제한다.
7. 테스트 회원의 잠금 상태를 다시 확인하고 정상 로그인한다. 다음 실패는 다시 1회부터 시작해야 한다.

전체 브라우저·실제 MySQL 시나리오의 수동 확인은 별도로 필요하다.
