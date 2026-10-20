# TJK-109 관리자 계정 해제 — 백엔드 코드리뷰

기준: PR #10의 `work/khs/TJK-109` 코드. 로그인 실패 정책 변경은 제외한다.

코드 폴더: `C:\kh_final\taxjikim\.tmp\tjk109-pr`

VS Code에서 이 폴더를 열고 `Ctrl+Shift+F`로 각 단계의 검색어를 찾는다. 경로는 이 폴더 기준이다.

## 전체 흐름

```text
요청 → JWT 쿠키 인증 → 관리자 권한 확인
→ Controller → Service → Mapper 인터페이스 → Mapper XML → DB

해제 처리:
회원 확인 → 상태 확인 → ACTIVE 및 수정 시각 저장
→ 기존 로그인 세션 종료 → 실패 기록 초기화 → 성공 응답
```

## 1. 어떤 요청을 받는가 — Controller

파일: `back/src/main/java/com/kh/back/auth/controller/AdminAccountLockApiController.java`

검색어: `class AdminAccountLockApiController`

```java
@RequestMapping("/api/v1/admin/account-locks")
```

컨트롤러의 공통 주소다. 아래 두 요청을 제공한다.

| 요청 | 메서드 | 역할 |
| --- | --- | --- |
| GET `/api/v1/admin/account-locks` | `list()` | 제한된 계정 목록 조회 |
| DELETE `/api/v1/admin/account-locks/{loginId}` | `unlock()` | 해당 계정의 제한 해제 |

DELETE는 회원을 삭제하는 것이 아니라 계정에 걸린 제한을 제거한다는 의미다.

```java
@GetMapping
public ApiResponse<List<LockedAccountDto>> list() {
    return ApiResponse.ok(accountLockService.getLockedAccounts());
}
```

서비스에서 받은 목록을 공통 응답 형식으로 감싼다.

```java
@DeleteMapping("/{loginId}")
public ApiResponse<Void> unlock(@PathVariable String loginId) {
    InputValidator.requireText("loginId", loginId);
    accountLockService.unlockAccount(loginId);
    return ApiResponse.ok(null);
}
```

- `@PathVariable`: 주소에 들어온 아이디를 받는다.
- `requireText`: 아이디가 비어 있지 않은지 확인한다.
- 컨트롤러는 SQL이나 해제 로직을 직접 작성하지 않고 서비스에 전달한다.
- `@RequiredArgsConstructor`가 final 서비스 필드의 생성자를 만들어 Spring이 주입한다.

발표: “조회와 해제 요청을 분리했고, 컨트롤러는 입력 확인과 서비스 호출을 담당합니다.”

## 2. 누가 호출할 수 있는가 — SecurityConfig

파일: `back/src/main/java/com/kh/back/common/config/SecurityConfig.java`

검색어: `/api/v1/admin/account-locks/**`

```java
.requestMatchers("/api/v1/admin/account-locks", "/api/v1/admin/account-locks/**")
.hasRole("ADMIN")
```

실행 순서는 컨트롤러보다 보안 필터가 먼저다. 기존 JWT 필터가 쿠키로 사용자를 인증하고, 이 규칙이 관리자 권한을 확인한다.

- 비회원: 401, 인증 필요
- 일반 회원: 403, 권한 부족
- 관리자: 컨트롤러로 전달

발표: “API를 직접 호출하더라도 서버에서 ADMIN 권한을 검사합니다.”

## 3. 서비스 인터페이스와 구현체

파일:

- `back/src/main/java/com/kh/back/auth/service/AccountLockService.java`
- `back/src/main/java/com/kh/back/auth/service/AccountLockServiceImpl.java`

검색어: `interface AccountLockService`, `class AccountLockServiceImpl`

인터페이스는 제공할 기능을 선언하고, 구현체는 처리 순서를 작성한다. 컨트롤러는 인터페이스를 주입받는다.

```java
List<LockedAccountDto> getLockedAccounts();
void unlockAccount(String loginId);
```

구현체는 다음 세 의존성을 사용한다.

| 필드 | 역할 |
| --- | --- |
| `UserSecurityMapper mapper` | DB 조회·상태 변경 |
| `LoginAttemptService attempts` | 기존 실패 기록 초기화 |
| `LoginSessionRepository sessions` | 기존 로그인 세션 종료 |

## 4. 목록은 어떻게 조회하는가 — Mapper와 SQL

구현체 검색어: `return mapper.findLockedAccounts()`

```java
public List<LockedAccountDto> getLockedAccounts() {
    return mapper.findLockedAccounts();
}
```

다음 파일에서 같은 이름을 찾는다.

- `back/src/main/java/com/kh/back/auth/mapper/UserSecurityMapper.java`
- `back/src/main/resources/mappers/auth/UserSecurityMapper.xml`

검색어: `findLockedAccounts`

Mapper 인터페이스의 메서드 이름과 XML의 SQL id가 연결된다. XML의 namespace는 인터페이스의 전체 클래스 이름이다.

```sql
SELECT USER_ID AS userId, LOGIN_ID AS loginId, USER_STATUS AS userStatus,
       UPDATED_AT AS updatedAt
FROM users
WHERE USER_STATUS IN ('LOCKED', 'BANNED')
ORDER BY USER_ID DESC
LIMIT 100
```

- 정상 회원은 DB 조회 단계에서 제외한다.
- `LOCKED`: 로그인 실패 잠금, `BANNED`: 관리자 이용 정지.
- 최근 회원 번호 순으로 최대 100개를 조회한다. 현재 페이징은 없다.
- `AS`는 DB 컬럼을 Java 필드 이름에 맞춘 별칭이다.

## 5. 응답에는 무엇이 담기는가 — DTO

파일: `back/src/main/java/com/kh/back/auth/dto/LockedAccountDto.java`

검색어: `record LockedAccountDto`

```java
public record LockedAccountDto(
    Long userId, String loginId, String userStatus, LocalDateTime updatedAt
)
```

조회 결과 전달에 필요한 네 값만 담는다. 목록 응답에는 비밀번호가 없다.

검색어: `getUpdatedAtJson`

```java
@JsonGetter("updatedAt")
public String getUpdatedAtJson() {
    return DateTimeUtil.toSeoulString(updatedAt);
}
```

`updatedAt`을 한국 시간대가 포함된 ISO 8601 문자열로 내보낸다. 기존 `users.UPDATED_AT`을 사용하며 별도 시간 컬럼을 추가하지 않았다.

## 6. 해제할 회원과 상태를 확인한다

파일: `back/src/main/java/com/kh/back/auth/service/AccountLockServiceImpl.java`

검색어: `public void unlockAccount(String loginId)`

```java
UserDto user = mapper.findForAuthentication(loginId);
```

먼저 해당 회원을 조회한다. XML에서 `findForAuthentication`을 찾으면 `FOR UPDATE`가 있다.

```sql
SELECT USER_ID AS userId, LOGIN_ID AS loginId, PASSWORD AS password,
       USER_ROLE AS userRole, USER_STATUS AS userStatus
FROM users
WHERE LOGIN_ID = #{loginId}
FOR UPDATE
```

`FOR UPDATE`는 트랜잭션 동안 해당 회원 행을 잠가 다른 상태 변경 요청과의 충돌을 줄인다. 이 조회에 비밀번호가 포함돼 있지만 내부 UserDto로만 사용하며 관리자 목록 응답으로 보내지 않는다.

서비스는 다음을 검사한다.

| 상황 | 오류 코드 |
| --- | --- |
| 회원이 없음 | `USER_NOT_FOUND` |
| LOCKED·BANNED가 아님 | `ACCOUNT_NOT_LOCKED` |
| 해제 UPDATE 건수가 1이 아님 | `ACCOUNT_UNLOCK_CONFLICT` |

휴면 등 다른 상태를 임의로 활성화하지 않는다.

## 7. DB 상태와 수정 시각을 변경한다

서비스 검색어: `mapper.unlockAccount(user.getLoginId())`

XML 검색어: `<update id="unlockAccount"`

```sql
UPDATE users
SET USER_STATUS = 'ACTIVE',
    UPDATED_AT = NOW()
WHERE LOGIN_ID = #{loginId}
  AND USER_STATUS IN ('LOCKED', 'BANNED')
```

- `ACTIVE`로 복구하면서 수정 시각도 갱신한다.
- SQL에서도 상태를 확인한다.
- `#{loginId}`는 MyBatis의 값 바인딩이다.
- 서비스는 변경 건수 1을 확인한 뒤 다음 처리로 진행한다.

참고: 같은 XML의 `lockAccount`에도 `UPDATED_AT = NOW()`와 ACTIVE 조건을 추가했다. 이는 잠금이 실제 발생한 시각을 갱신하기 위한 변경이며, 실패 횟수 기준을 바꾸는 것은 아니다.

## 8. 기존 로그인 세션을 종료한다

서비스 검색어: `sessions.revokeCurrentSession`

파일: `back/src/main/java/com/kh/back/auth/security/LoginSessionRepository.java`

검색어: `void revokeCurrentSession`

```java
redisTemplate.delete(PREFIX + userId);
```

Redis의 현재 로그인 정보(`LOGIN:user-id:회원번호`)를 삭제한다. 계정이 ACTIVE로 돌아왔을 때 잠금 이전의 인증이 다시 사용되지 않게 하려는 처리다.

브라우저 쿠키를 원격에서 지우는 것이 아니다. 쿠키가 남아 있더라도 서버의 유효 세션과 일치하지 않아 기존 인증을 사용할 수 없게 한다.

## 9. 로그인 실패 기록을 초기화한다

서비스 검색어: `attempts.loginSucceeded`

파일: `back/src/main/java/com/kh/back/auth/security/LoginAttemptServiceImpl.java`

검색어: `void loginSucceeded`

PR 기준 코드는 기존 구현을 그대로 사용한다.

```java
attemptsCache.invalidate(loginId);
blockTimeMap.remove(loginId);
```

- Caffeine의 실패 횟수를 삭제한다.
- 메모리 Map의 일시 차단 종료 시각을 삭제한다.
- 이름은 로그인 성공이지만 실패 기록을 초기화하는 기능이라 관리자 해제에서도 재사용했다.

중요: 개인 브랜치의 Redis 실패 기록 전환은 별도 작업이다. 이 관리자 PR은 실패 기록 저장 방식이나 차단 횟수를 변경하지 않는다.

## 10. 중간에 오류가 발생하면 어떻게 되는가

파일: `AccountLockServiceImpl.java`

검색어: `@Transactional`

- 클래스의 `@Transactional(readOnly = true)`는 목록 조회의 기본 설정이다.
- 해제 메서드의 `@Transactional`은 쓰기가 가능한 트랜잭션으로 처리한다.
- 예상하지 못한 예외가 발생하면 DB 상태 변경은 롤백되고 성공 응답을 보내지 않는다.
- DB와 Redis 전체가 하나의 분산 트랜잭션은 아니다. 이미 삭제된 Redis 세션까지 자동 복구되지는 않는다.

파일: `back/src/main/java/com/kh/back/common/exception/GlobalExceptionHandler.java`

검색어: `handleApi`

`ApiException`을 HTTP 상태·오류 코드·메시지가 포함된 공통 실패 응답으로 변환한다.

## 발표용 요약

“관리자 전용 GET과 DELETE API를 만들었습니다. 서버에서 ADMIN 권한을 검사하고, DB에서 BANNED·LOCKED 계정만 조회합니다. 해제 요청은 회원 상태를 확인한 뒤 ACTIVE와 수정 시각을 저장합니다. 이어서 기존 로그인 세션과 실패 기록을 삭제하고 성공 응답을 반환합니다.”

## 확인한 범위

- 사용자가 두 상태 각각 해제, 목록에서 제거, 재로그인 성공을 확인했다.
- PR 분리 브랜치에서 관리자 권한·서비스 테스트 12개와 프론트 빌드가 통과했다.
- 실제 MySQL의 동시 요청·장애 복구 전체를 통합 검증한 것은 아니다.
