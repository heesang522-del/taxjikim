# 인증·회원 백엔드 사용 설명서

현재 코드를 읽는 출발점이다. 코드나 정책을 변경하는 문서가 아니다.
2026-10-20 작업 폴더 기준이며, 로그인 실패 정책은 검토 중이다.

## 목차

- [패키지 구분과 코드 찾기](#패키지-구분과-코드-찾기)
- [auth: 로그인 실패 기록 사용법](#auth-로그인-실패-기록-사용법)
- [LoginAttemptServiceImpl을 위에서부터 읽기](#loginattemptserviceimpl을-위에서부터-읽기)
- [현재 동작과 주의할 부분](#현재-동작과-주의할-부분)
- [common: 공통 보안과 오류 처리](#common-공통-보안과-오류-처리)
- [user: 회원 정보 관리](#user-회원-정보-관리)
- [기존 문서 모음](#기존-문서-모음)

## 패키지 구분과 코드 찾기

세 패키지는 별도 서버가 아니라, 같은 백엔드 안에서 업무별로 나눈 폴더다.

| 폴더 | 담당 업무 | 예시 |
| --- | --- | --- |
| [auth](../back/src/main/java/com/kh/back/auth/) | 회원임을 확인하고 로그인 상태를 관리 | 로그인, 실패 기록, 로그아웃, 이메일 인증 |
| [common](../back/src/main/java/com/kh/back/common/) | 여러 기능에서 사용하는 공통 처리 | 요청의 JWT 검사, 접근 권한, 오류 응답 |
| [user](../back/src/main/java/com/kh/back/user/) | 회원 정보의 저장·조회·변경 | 회원가입, 프로필 수정, 탈퇴 |

이 문서의 파일 링크를 누르면 코드로 이동할 수 있다. 편집기에서 링크 이동이 지원되지 않으면
`Ctrl + Shift + F`로 표에 있는 클래스 이름이나 메서드 이름을 검색한다.

| 찾을 내용 | 검색어 | 코드 |
| --- | --- | --- |
| 로그인 판정 | `class CustomAuthenticationProvider` | [CustomAuthenticationProvider.java](../back/src/main/java/com/kh/back/auth/security/CustomAuthenticationProvider.java) |
| 실패 기록의 기능 목록 | `interface LoginAttemptService` | [LoginAttemptService.java](../back/src/main/java/com/kh/back/auth/security/LoginAttemptService.java) |
| 실패 기록의 실제 구현 | `class LoginAttemptServiceImpl` | [LoginAttemptServiceImpl.java](../back/src/main/java/com/kh/back/auth/security/LoginAttemptServiceImpl.java) |
| 결과 데이터의 형태 | `record LoginAttemptDto` | [LoginAttemptDto.java](../back/src/main/java/com/kh/back/auth/dto/LoginAttemptDto.java) |
| 관리자 잠금 해제 | `class AccountLockServiceImpl` | [AccountLockServiceImpl.java](../back/src/main/java/com/kh/back/auth/service/AccountLockServiceImpl.java) |

## auth: 로그인 실패 기록 사용법

### 두 클래스는 어떻게 협력하는가?

`CustomAuthenticationProvider`가 비밀번호와 회원 상태를 검사하고 로그인 허용 여부를 결정한다.
`LoginAttemptServiceImpl`은 요청받은 아이디의 실패 횟수와 차단 시간을 Redis에서 관리한다.
실패 기록 담당은 직접 비밀번호를 비교하거나 JWT 쿠키를 만들지 않는다.

```text
CustomAuthenticationProvider: 회원 상태와 기존 차단 상태 확인
  → getAttempt(loginId): 실패 기록 조회
  → 기존 차단이 있으면 로그인 거절
  → 아이디가 없거나 비밀번호가 틀리면 loginFailed(loginId)
      → Redis에서 실패 횟수·차단 시간 갱신
      → 갱신된 결과로 안내하거나, 잠금 대상이면 DB 회원 상태도 LOCKED로 변경
  → 비밀번호 검사까지 통과하면 loginSucceeded(loginId)
      → Redis 실패 기록 삭제 후 인증 성공 결과 반환
```

### 왜 Service와 ServiceImpl 두 개가 있는가?

`LoginAttemptService`는 호출할 수 있는 기능의 목록인 **인터페이스**다.
`LoginAttemptServiceImpl`은 그 기능을 실제로 수행하는 **구현 클래스**다.
`Impl`은 implementation(구현)의 줄임말이다.

인증 담당 코드가 `LoginAttemptService`를 요청하면 Spring이 등록된 구현체를 연결한다.
호출자가 구현체를 직접 `new`로 만들거나 Redis 명령을 직접 작성할 필요는 없다.

| 호출 | 용도 | 반환값과 변경 |
| --- | --- | --- |
| `getAttempt(loginId)` | 로그인 검사 전에 현재 상태 확인 | `LoginAttemptDto` 반환. 횟수 증가 없음 |
| `loginFailed(loginId)` | 로그인 실패가 확인됐을 때 기록 | 조건에 따라 횟수·차단 시각 갱신 후 DTO 반환 |
| `loginSucceeded(loginId)` | 로그인 성공 또는 관리자 해제 시 기록 초기화 | Redis의 해당 기록 삭제. 반환값 없음 |

관리자 해제도 `loginSucceeded`를 사용한다. 이름과 달리 로그인 성공 전용 호출은 아니다.
DB의 `LOCKED`나 `BANNED`를 `ACTIVE`로 바꾸는 일은 관리자 서비스에서 별도로 한다.

## LoginAttemptServiceImpl을 위에서부터 읽기

대상 코드: [LoginAttemptServiceImpl.java](../back/src/main/java/com/kh/back/auth/security/LoginAttemptServiceImpl.java)

### 1. 클래스 위의 어노테이션

- `@Service`: Spring이 이 클래스를 서비스 객체로 관리하게 한다.
- `@RequiredArgsConstructor`: Lombok이 `final` 의존성을 받는 생성자를 만든다.
  여기서는 생성자를 통해 `StringRedisTemplate`을 받는다.
- `@Override`: 인터페이스에 선언된 메서드를 이 클래스에서 구현한다는 표시다.

### 2. redisTemplate, PREFIX, SEOUL

`redisTemplate`은 Java에서 Redis로 명령을 보내는 도구다.
Redis는 여기서 아이디별 로그인 실패 기록을 보관하는 저장소다.

`PREFIX`는 기록 이름 앞에 붙이는 고정 문자열이다. 아이디가 `sample-user`면 다음 이름을 사용한다.

```text
LOGIN_ATTEMPT:v2:sample-user
  count = 실패 횟수
  until = 차단 종료 시각(1970년 기준 밀리초), 차단이 없으면 0
```

Redis의 Hash는 한 이름 아래 여러 필드와 값을 저장하는 형태다.
현재 기록에는 `count`, `until` 두 필드가 있다.
`v2`는 키 이름의 일부일 뿐 자동 버전 관리 기능은 아니다.

`SEOUL`은 응답 시각을 한국 시간으로 변환할 때 사용한다.

### 3. ATTEMPT_SCRIPT: 왜 Java 안에 다른 언어가 있는가?

`DefaultRedisScript<List>`는 Redis에서 실행할 스크립트와 반환 타입을 담는다.
`"""` 사이의 문자열은 Lua 코드이고, Java 자체가 이 부분을 계산하는 것은 아니다.
`List`는 실행 결과를 목록으로 받는다는 뜻이다.

실패 기록을 읽고, 1을 더하고, 다시 저장하는 작업을 각각 요청하면
동시 요청 두 개가 같은 이전 횟수를 읽고 같은 숫자를 저장해 실패가 누락될 수 있다.
현재는 이 전체 작업을 Redis 스크립트 한 번으로 실행해 중간에 다른 요청이 끼어들지 않게 한다.
이는 Redis 안에서의 처리 보장이며 MySQL 변경까지 한 트랜잭션으로 묶는다는 뜻은 아니다.

### 4. Lua에서 현재 시각과 기록 읽기

```lua
local time = redis.call('TIME')
local now = tonumber(time[1]) * 1000 + math.floor(tonumber(time[2]) / 1000)
local count = tonumber(redis.call('HGET', KEYS[1], 'count') or '0')
local untilAt = tonumber(redis.call('HGET', KEYS[1], 'until') or '0')
```

- `local`: Lua 안에서 사용하는 변수 선언이다.
- `redis.call`: Redis 명령을 실행한다. `TIME`은 Redis 서버 시각, `HGET`은 Hash 필드 조회다.
- Lua 목록은 1부터 시작한다. `time[1]`은 초, `time[2]`는 마이크로초다.
- 초는 1,000을 곱하고 마이크로초는 1,000으로 나눠 모두 밀리초로 맞춘다.
  `math.floor`는 소수점 아래를 버린다.
- `tonumber`는 Redis에서 읽은 값을 숫자로 변환한다.
- `KEYS[1]`은 Java가 전달한 저장 이름이다.
- `or '0'`은 기록이 없을 때 0을 사용한다는 뜻이다.

### 5. 실패 횟수를 늘리는 조건

```lua
if ARGV[1] == 'fail' and count < 10 and untilAt <= now then
```

다음 세 조건을 모두 만족할 때만 안쪽 코드를 실행한다.

1. Java에서 전달한 작업 종류(`ARGV[1]`)가 `fail`이다.
2. 아직 실패 횟수가 10회 미만이다.
3. 차단 종료 시각이 지났거나 차단 기록이 없다.

`read` 요청은 이 부분에 들어가지 않는다. 차단 중의 `fail` 요청도 횟수·종료 시각을 바꾸지 않는다.

### 6. 횟수 증가와 차단 시간 저장

```lua
count = count + 1
local durations = {[6]=30000, [7]=60000, [8]=180000, [9]=300000}
untilAt = durations[count] and now + durations[count] or 0
redis.call('HSET', KEYS[1], 'count', count, 'until', untilAt)
```

`durations`는 실패 횟수를 차단 시간에 연결한 표이며 단위는 밀리초다.

| 실패 횟수 | 처리 |
| --- | --- |
| 1~5회 | 횟수만 기록 |
| 6회 | 30초 차단 |
| 7회 | 1분 차단 |
| 8회 | 3분 차단 |
| 9회 | 5분 차단 |
| 10회 | DTO에 계정 잠금 대상이라고 표시. DB 변경은 호출자가 수행 |

`durations[count]`에 값이 있으면 현재 시각에 그 시간을 더하고, 없으면 `untilAt`을 0으로 둔다.
`HSET`은 갱신한 실패 횟수와 종료 시각을 Redis에 저장한다.
차단 시간이 지나도 실패 횟수는 남아 있으므로 다음 실패에서 다음 단계로 진행한다.

### 7. Java로 결과 돌려주기

```lua
return {count, untilAt, now}
```

Java에서는 `execute` 메서드가 아래 호출로 이 결과를 받는다.

```java
redisTemplate.execute(ATTEMPT_SCRIPT, List.of(key(loginId)), action);
```

첫 번째 인자는 실행할 스크립트, 두 번째는 저장 이름 목록, 마지막은 `read` 또는 `fail`이다.
반환 목록의 순서는 **실패 횟수 → 차단 종료 시각 → Redis 현재 시각**이다.
Lua는 1부터 시작하지만 Java 목록은 0부터 시작하므로 `values.get(0)`이 실패 횟수다.

결과가 없거나 항목이 세 개가 아니면 오류를 낸다.
Redis 접근 오류도 정상적인 로그인 실패로 바꾸지 않고 호출자에게 전파한다.

### 8. LoginAttemptDto로 변환하기

`execute`는 결과 숫자를 변환해 [LoginAttemptDto](../back/src/main/java/com/kh/back/auth/dto/LoginAttemptDto.java)를 만든다.
DTO는 계산 결과를 다른 코드로 전달하기 위한 데이터 묶음이다.

| DTO 필드 | 계산과 의미 |
| --- | --- |
| `failedAttempts` | 기록된 실패 횟수 |
| `remainingAttempts` | `Math.max(0, 6 - count)`: 첫 차단까지 남은 실패 횟수. 음수는 0으로 처리 |
| `blockedUntil` | 종료 시각이 현재보다 뒤이면 한국 시간으로 변환, 아니면 `null` |
| `serverTime` | 계산에 사용한 Redis 현재 시각을 한국 시간으로 변환 |
| `locked` | `count >= 10`이면 `true` |

`Instant.ofEpochMilli`는 밀리초 숫자를 시각으로 바꾸고,
`atZone(SEOUL).toOffsetDateTime()`은 한국 시간대 정보가 붙은 날짜·시각으로 바꾼다.
`조건 ? 값1 : 값2`는 조건이 참이면 값1, 거짓이면 값2를 고르는 Java 문법이다.
프론트는 종료 시각과 서버 시각을 받아 남은 시간을 계산할 수 있다.

예를 들어 6회째 실패하면 `failedAttempts=6`, `remainingAttempts=0`,
`blockedUntil=현재보다 30초 뒤`, `locked=false`다. 아직 DB 계정 잠금은 아니다.

### 9. loginSucceeded: 기록 삭제

```java
redisTemplate.delete(key(loginId));
```

해당 아이디의 Hash 전체를 지워 실패 횟수와 차단 시각을 함께 초기화한다.
다음 조회에서는 기록이 없으므로 0회로 읽는다.
이 클래스는 기록에 자동 만료 시간(TTL)을 설정하지 않는다.
따라서 정상 처리 흐름에서는 로그인 성공 또는 관리자 해제 때 삭제한다.
Redis 재시작·장애 후 보존 여부는 별도의 Redis 영속화 설정에 달려 있다.

### 10. key: 같은 아이디의 저장 이름 맞추기

```java
return PREFIX + loginId.trim().toLowerCase(Locale.ROOT);
```

앞뒤 공백을 지우고 영문을 소문자로 바꾼 뒤 접두사를 붙인다.
`Locale.ROOT`는 실행 PC 언어 설정에 따라 소문자 변환 규칙이 달라지지 않게 한다.
입력 아이디의 형식 검증이나 DB의 대소문자 비교 규칙을 설정하는 코드는 아니다.

## 현재 동작과 주의할 부분

- 위 횟수·시간 표는 현재 구현 설명이며 확정 정책으로 간주하지 않는다.
- 실패 기록은 아이디별로 공유한다. 다른 사람이 쌓은 기록을 실제 사용자가 떠안는 문제는 보류 중이다.
- 현재 인증 담당 코드는 회원 상태를 비밀번호 검사보다 먼저 확인한다. 상태 안내 순서는 별도 검토 대상이다.
- 존재하지 않는 아이디도 실패 기록을 만들 수 있다. 이 클래스는 회원 존재 여부를 조회하지 않는다.
- `locked`는 Redis 횟수 기준 결과이고, DB의 회원 상태와 동일한 필드는 아니다.
- 횟수·시간 숫자는 아직 코드에 직접 들어 있다. 이번 문서화에서는 정책·구조를 변경하지 않았다.

## common: 공통 보안과 오류 처리

| 읽을 코드 | 역할 |
| --- | --- |
| [SecurityConfig.java](../back/src/main/java/com/kh/back/common/config/SecurityConfig.java) | URL별 인증·권한 규칙과 보안 필터 연결 |
| [JwtTokenProvider.java](../back/src/main/java/com/kh/back/common/security/JwtTokenProvider.java) | JWT 생성·검증 관련 처리 |
| [JwtAuthenticationFilter.java](../back/src/main/java/com/kh/back/common/security/JwtAuthenticationFilter.java) | 요청의 JWT를 검사해 인증 정보를 연결 |

상세한 공통 응답·예외 설명은 [기존 학습 노트](backend-review-guide.md)를 참고한다.
이 절은 코드 위치 안내이며 common 전체를 재검토한 설명은 아니다.

## user: 회원 정보 관리

| 읽을 코드 | 역할 |
| --- | --- |
| [UserApiController.java](../back/src/main/java/com/kh/back/user/controller/UserApiController.java) | 회원 관련 API의 진입점 |
| [UserServiceImpl.java](../back/src/main/java/com/kh/back/user/service/UserServiceImpl.java) | 회원 관련 업무 처리 |
| [UserMapper.java](../back/src/main/java/com/kh/back/user/mapper/UserMapper.java) | 회원 DB 작업을 호출하는 인터페이스 |
| [UserMapper.xml](../back/src/main/resources/mappers/user/UserMapper.xml) | 회원 조회·저장·변경 SQL |

상세 설명은 [기존 학습 노트](backend-review-guide.md)를 참고한다.
이 절은 코드 위치 안내이며 user 전체를 재검토한 설명은 아니다.

## 기존 문서 모음

| 문서 | 용도와 기준 |
| --- | --- |
| [백엔드 코드리뷰 학습 노트](backend-review-guide.md) | auth·common·user 전체 학습 자료. 2026-10-15 기준이며 실패 기록의 Caffeine 설명 등 현재 코드와 다른 부분이 있음 |
| [관리자 해제 코드리뷰](admin-unlock-code-review.md) | 분리된 관리자 PR을 설명하는 임시 자료. 개인 브랜치와 구현 차이에 주의 |
| [관리자 티켓](tjk-109-admin-unlock.md) | TJK-109 관련 문서 |
| [로그인 실패 흐름 검토](login-failure-flow-review.md) | 이전 검토 자료. 현재 코드와 대조하며 읽기 |
| [로그인 실패 정책 검토](login-failure-policy.md) | 정책 논의 기록. 확정 사양으로 사용하지 않기 |
| [API 초안](api-draft.md) | API 관련 기존 문서 |
| [코드 컨벤션](code-convention.md) | 코드를 작성·수정할 때 따를 규칙 |

현재 로그인 실패 기록을 이해할 때는 이 문서와 연결된 실제 코드를 먼저 읽는다.
