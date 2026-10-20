# 백엔드 코드리뷰 학습 노트

> 작성 기준: 2026-10-15 현재 작업 폴더의 코드.
> 변경 이력이 아니라, **현재 코드가 무엇을 하고 왜 역할을 나눴는지 설명하기 위한 자료**다.
> 사용자가 맡은 인증·회원 기능과 이 기능에 필요한 공통 처리만 설명한다.
> 설계 의도와 실제 구현이 다른 부분은 마지막의 ‘현재 한계’에 표시했다. 미완료 기능을 완료했다고 설명하지 않는다.

## 1. 시간이 부족하면 먼저 읽을 부분

1. 2~4장: 패키지, 계층, DTO의 역할
2. 5~6장: 회원가입과 이메일 인증
3. 7~10장: 로그인, JWT·쿠키·Redis, 재발급·로그아웃
4. 11~13장: 계정 찾기·회원정보 수정·탈퇴
5. 16~18장: 구현의 한계, 예상 질문, 발표용 요약

모든 클래스 이름을 외우기보다 다음 세 가지를 구분한다.

- **DB에 저장할 정보인가?** → MySQL과 Mapper
- **현재 요청의 사용자가 누구인가?** → JWT 필터와 Spring Security
- **잠깐 기억할 인증 정보인가?** → Redis 또는 Caffeine. 두 저장소의 실제 용도는 서로 다르다.

## 2. 전체 패키지 구조

Java 코드 기준 위치는 `back/src/main/java/com/kh/back/`이다.

| 패키지 | 담당하는 일 | 대표 클래스 |
| --- | --- | --- |
| `auth` | 로그인·로그아웃·토큰 재발급·이메일 인증·계정 찾기 | `AuthServiceImpl`, `EmailVerificationServiceImpl` |
| `auth/security` | 회원 인증, 실패 횟수, 로그인 식별값 관리 | `CustomAuthenticationProvider`, `LoginSessionRepository` |
| `user` | 회원가입·회원 조회·프로필 수정·탈퇴 | `UserServiceImpl`, `UserMapper` |
| `common/config` | 보안·비밀번호 해시·업로드 경로 설정 | `SecurityConfig`, `PasswordEncoderConfig`, `WebMvcConfig` |
| `common/security` | JWT 생성과 요청의 JWT 검사 | `JwtTokenProvider`, `JwtAuthenticationFilter` |
| `common/dto`, `exception`, `validation` | 공통 응답·오류·입력 규칙 | `ApiResponse`, `GlobalExceptionHandler`, `PasswordValidator` |
| `common/util` | 파일 저장·날짜 변환 등 공통 도구 | `FileUtil`, `DateTimeUtil` |

**왜 auth와 user를 나눴나?**

회원이라는 데이터 자체를 다루는 일과, 그 회원을 인증하는 일은 책임이 다르기 때문이다.
예를 들어 닉네임 수정은 회원 업무이고, JWT 재발급은 인증 업무다. 다만 인증 시 회원 정보를 확인해야 하므로 두 패키지는 협력한다.
완전히 독립된 서버 두 개가 아니라, 한 Spring 애플리케이션 안의 역할 구분이다.

**리뷰에서 말하기**

> 회원 정보의 저장·변경은 user에, 로그인과 토큰·이메일 인증은 auth에 두었습니다. JWT 검사와 공통 응답처럼 여러 기능에서 사용하는 처리는 common으로 분리했습니다.

## 3. Controller → Service → Mapper → XML

일반적인 API 요청은 다음 순서로 흐른다. 인증이 필요한 요청은 Controller에 도달하기 전에 보안 필터를 통과한다.

```text
HTTP 요청
  → Spring Security 필터
  → Controller: 요청 해석·서비스 호출·응답 구성
  → Service: 업무 규칙과 처리 순서
  → Mapper 인터페이스
  → MyBatis XML의 SQL
  → MySQL
```

| 계층 | 여기서 하는 일 | 여기서 하지 않으려는 일 |
| --- | --- | --- |
| Controller | JSON·파일·경로 값을 받기, 로그인 사용자 받기, 상태 코드 정하기 | SQL 작성, 비밀번호 해시 등 업무 처리 |
| Service | 이메일 인증 확인, 비밀번호 비교·해시, 여러 작업 순서와 트랜잭션 관리 | 화면 배치, HTTP 입력 형식 처리 |
| Mapper | Java 메서드로 DB 작업의 호출 규격 정의 | 인증 정책 결정 |
| XML | 실제 SELECT·INSERT·UPDATE·DELETE | 화면 표시 문구 결정 |

### Mapper 인터페이스와 XML을 왜 둘 다 두나?

`UserMapper.findByLoginId(loginId)`를 호출하면 MyBatis가 XML에서 같은 namespace와 메서드 id를 찾아 실행한다.
인터페이스는 Java 호출 규격, XML은 SQL 내용이다. 둘을 따로 호출하는 것이 아니다.

- XML namespace: `com.kh.back.user.mapper.UserMapper`
- XML statement id: `findByLoginId`
- SQL의 `LOGIN_ID = #{loginId}`: 값을 SQL 문자열에 직접 이어 붙이지 않고 바인딩한다.
- `USER_ID AS userId`: DB 컬럼 이름과 Java 필드 이름을 연결한다.
- `@Param`: 인자가 여러 개일 때 XML에서 사용할 이름을 지정한다.

### Service 인터페이스와 Impl은 왜 나눴나?

`UserService`에는 제공하는 기능의 규격을, `UserServiceImpl`에는 구현을 둔다.
Controller는 인터페이스를 주입받으므로 세부 구현에 덜 의존하고, 테스트에서는 가짜 구현으로 바꾸기 쉽다.
단, 지금 구현체가 여러 개라는 뜻은 아니다. 프로젝트 컨벤션으로 적용한 분리 방식이며, 모든 클래스가 완벽히 통일된 상태는 아니다.

### 생성자 주입과 Lombok

`private final UserService userService`는 이 객체가 다른 객체의 기능을 사용한다는 뜻이다.
Spring이 객체를 만들 때 생성자로 필요한 의존성을 전달한다. `@RequiredArgsConstructor`는 초기화가 필요한 final 필드의 생성자를 만들어주는 Lombok 기능이다.
직접 `new UserServiceImpl(...)`을 반복하지 않아도 되는 이유다.

## 4. DTO는 무엇이고 왜 나눴나?

DTO는 **어떤 처리에 필요한 값을 전달하는 Java 객체**다. DB 테이블이 아니며, DTO 파일이 늘어도 테이블이 생기지 않는다.
클라이언트 요청, 서버 응답, 내부 처리 결과는 필요한 정보와 공개해도 되는 정보가 서로 다르다.

### 실제 DTO별 역할

| DTO | 담는 값 | 별도로 둔 이유 |
| --- | --- | --- |
| `LoginRequestDto` | loginId, password, rememberMe | 로그인에 필요한 요청값만 받기 |
| `LoginResponseDto` | accessToken, tokenType, userId, loginId, userRole | 로그인 결과의 응답 규격. 비밀번호를 반환하지 않기 |
| `TokenResponseDto` | accessToken, tokenType | 재발급에는 로그인 전체 결과가 필요하지 않음 |
| `RefreshTokenDto` | refreshToken, jti | 내부에서 새 토큰과 Redis에 기록할 식별값을 함께 전달 |
| `EmailVerificationRequestDto` | email, code | 이메일 발송·확인 요청. 발송에서는 code가 필요하지 않음 |
| `AccountLookupRequestDto` | name, email, code | 이름·이메일에 해당하는 아이디 찾기 |
| `PasswordResetRequestDto` | loginId, email, newPassword | 특정 계정의 비밀번호 재설정 |
| `AccountUnlockRequestDto` | token | 잠금 해제 링크의 일회성 토큰을 받기 |
| `AccountRecoveryRequestDto` | loginId, email, code | 이메일 인증으로 휴면 계정을 재활성화하는 요청 |
| `UserUpdateRequestDto` | nickname, email, currentPassword, newPassword | 회원 수정 요청에서 허용한 값만 받기 |
| `UserDto` | 회원 번호·아이디·이름·이메일·권한·상태·날짜 등 | DB 조회 결과와 회원 정보 전달. 현재는 가입 요청에도 재사용 |
| `EmailVerificationDto` (미사용으로 삭제) | 예전 인증번호·시각·인증 여부 구조 | 현재 인증 캐시는 DTO 대신 String·Boolean을 저장하므로 해당 클래스는 제거함 |

### UserDto 하나로 전부 처리하면 안 되나?

회원 조회에는 역할·상태·가입 시각이 필요하지만, 비밀번호 변경 요청에서는 현재·새 비밀번호만 필요하다.
큰 DTO를 모든 수정 요청에 사용하면 역할이나 상태처럼 사용자가 수정하면 안 되는 값까지 입력 구조에 섞인다.
현재 수정 API는 `UserUpdateRequestDto`에서 필요한 값만 꺼내고, 수정 대상 아이디는 인증된 사용자에게서 가져온다.
‘요청이 보낸 userId를 믿고 그 회원을 수정하는 구조’가 아니다.

다만 **현재 DTO 분리가 완벽한 것은 아니다.**

- 가입 요청은 여전히 `UserDto`를 사용한다. 별도 가입 DTO가 있는 것은 아니다.
- `UserUpdateRequestDto`도 닉네임·이메일·비밀번호 수정 요청에서 함께 쓴다. 요청에 따라 일부 필드는 비어 있다.
- 아이디 찾기·재설정의 발송 요청에서도 최종 처리용 DTO를 재사용한다.
- 실제 가입 INSERT는 역할·상태를 어떤 값으로 저장하는지 SQL까지 함께 봐야 한다. DTO 분리만으로 권한 보호가 완성되지는 않는다.

### record와 일반 클래스가 섞인 이유

요청 record는 입력값 묶음을 간단히 표현한다. 필드의 접근자는 `request.email()`처럼 호출하고 setter는 없다.
`UserDto`는 MyBatis 조회·가입 후 생성된 번호 반영 등에 사용하는 가변 객체라 getter·setter를 둔다.
record 자체가 입력 검증을 해주는 것은 아니다. 필요한 검사는 별도로 호출한다.

### 비밀번호가 응답으로 나가지 않는 이유

`UserDto.password`에는 `@JsonProperty(WRITE_ONLY)`가 있어 요청에서는 받지만 JSON 응답에는 포함하지 않는다.
`@ToString(exclude = "password")`는 객체 로그에 비밀번호가 섞이지 않도록 한다.
회원 조회 서비스에서도 응답용 비밀번호 값을 비운다. DB 인증용 조회는 해시가 필요하므로 Mapper를 직접 사용하는 흐름과 구분한다.

**리뷰에서 말하기**

> DTO는 테이블이 아니라 전달할 데이터의 규격입니다. 로그인 요청과 응답은 필요한 값과 공개 범위가 달라 분리했고, 회원 수정은 사용자가 변경할 수 있는 값만 요청 DTO로 받습니다. 다만 가입용 UserDto 재사용 등 추가로 분리할 여지는 남아 있습니다.

## 5. 회원가입 흐름

읽을 파일:

- [UserApiController](../back/src/main/java/com/kh/back/user/controller/UserApiController.java)
- [UserServiceImpl](../back/src/main/java/com/kh/back/user/service/UserServiceImpl.java)
- [UserMapper XML](../back/src/main/resources/mappers/user/UserMapper.xml)

```text
이메일 인증 완료
  → POST /api/v1/users
  → UserApiController
  → UserServiceImpl.joinUser
  → 필수값·길이·비밀번호 조건 확인
  → 서버에 이메일 인증 완료 기록이 있는지 확인
  → BCrypt 비밀번호 해시 생성
  → 선택 프로필 파일 저장
  → UserMapper.insertUser
  → 가입에 사용한 이메일 인증 완료 기록 제거
  → 생성된 회원 번호를 201 응답으로 반환
```

프로필이 없으면 JSON 요청을, 사진이 있으면 multipart 요청을 받는다. Controller의 메서드는 둘이지만 같은 `joinUser`를 호출한다.
multipart는 텍스트 값과 파일을 함께 보내는 요청 형식이며 파일 타입은 `MultipartFile`로 받는다.
프론트의 가입 화면은 두 단계지만 DB에는 ‘1단계 가입 테이블’과 ‘2단계 프로필 테이블’을 따로 생성하지 않는다. 최종 가입에서 한 번 요청한다.

### 중복 확인과 DB UNIQUE는 역할이 다르다

중복 확인 API는 사용 가능한 값인지 미리 안내한다. `true`면 이미 사용 중이라는 뜻이다.
확인 직후 다른 사용자가 같은 값을 등록할 수 있으므로, 최종 중복 방지는 DB의 UNIQUE 제약이 담당한다.
‘중복 확인을 눌렀으니 DB 제약은 필요 없다’고 설명하면 안 된다.

### 비밀번호는 암호화해서 나중에 복호화하는가?

아니다. `BCryptPasswordEncoder.encode`로 **단방향 해시**를 저장한다.
로그인 때는 `matches(입력값, 저장된 해시)`로 비교한다. 원래 비밀번호를 꺼내 비교하지 않는다.
같은 비밀번호를 여러 번 encode해도 salt 때문에 결과가 다를 수 있으므로, 해시 문자열끼리 단순 비교하면 안 된다.

### 새 비밀번호 공통 조건

`PasswordValidator`가 가입·변경·재설정에 동일하게 적용된다.

- 8~20자
- 공백 금지
- 영문·숫자·특수문자 `!@#$%^&*`를 각각 하나 이상 포함
- 기존 비밀번호로 로그인하거나 현재 비밀번호를 대조할 때는 새 규칙을 강제하지 않음

지정 특수문자 포함을 검사하는 것이며, 그 외 문자를 전부 금지하는 허용 문자 목록 검사와는 다르다.
비밀번호 확인 값은 오타 방지용 프론트 검사다. 현재 가입 API는 확인 값을 별도로 받지 않는다.

## 6. 이메일 인증은 어디에 저장하는가?

읽을 파일: [EmailVerificationServiceImpl](../back/src/main/java/com/kh/back/auth/service/EmailVerificationServiceImpl.java)

| 저장소 | 키 → 값 | 만료 시간 |
| --- | --- | --- |
| `verificationCodes` | 이메일 → 인증번호 | 발급·저장 후 5분 |
| `verifiedEmails` | 이메일 → true | 인증 확인 성공 후 10분 |

둘 다 **Caffeine이라는 Java 서버 내부 메모리 캐시**다. Redis나 MySQL에 저장하는 구조가 아니다.

1. 발송 요청에서 인증번호를 생성한다.
2. 이메일을 키로 인증번호를 캐시에 넣는다.
3. `JavaMailSender`로 메일을 발송한다.
4. 사용자가 보낸 번호와 캐시 값을 비교한다.
5. 일치하면 원래 번호를 지우고 인증 완료 기록을 만든다.
6. 회원가입·이메일 변경 등 실제 업무가 성공하면 인증 완료 기록도 제거한다.

**왜 번호와 인증 완료 기록을 나누나?**

번호를 확인하는 요청과 실제 가입 요청은 별개다. 번호를 한 번 사용한 뒤 다시 재사용하지 않게 하면서, 잠시 뒤 들어오는 가입 요청이 인증 사실을 확인할 수 있도록 나눈 것이다.
브라우저의 ‘인증 완료’ 표시만 믿지 않고 가입 서비스에서 다시 확인하는 이유이기도 하다.

현재 인증 메일은 `[TaxJiKim] 이메일 인증번호 안내` 제목과 5분 유효 안내를 사용한다.
발신 표시 이름은 TaxJiKim이며 실제 주소는 기존 메일 계정을 사용한다. 비밀번호·실제 인증번호를 문서나 로그 예시에 남기지 않는다.

주의: 서버 재시작 시 캐시는 사라진다. 현재 번호 생성은 `Math.random()` 기반이며, 인증 용도·횟수 제한까지 완성된 설계라고 설명하지 않는다.

## 7. 로그인: 사용자를 처음 확인하는 과정

읽을 파일:

- [AuthServiceImpl](../back/src/main/java/com/kh/back/auth/service/AuthServiceImpl.java)
- [CustomAuthenticationProvider](../back/src/main/java/com/kh/back/auth/security/CustomAuthenticationProvider.java)
- [CustomUserDetailsService](../back/src/main/java/com/kh/back/auth/security/CustomUserDetailsService.java)

```text
POST /api/v1/auth/sessions
  → AuthApiController.login
  → AuthServiceImpl.login
  → CustomAuthenticationProvider.authenticate
      → 일시 차단 확인
      → CustomUserDetailsService에서 아이디로 회원 조회
      → BCrypt로 비밀번호 비교
      → 회원 상태 확인
      → 성공 시 실패 기록 초기화
  → LAST_LOGIN_AT 갱신
  → 로그인 식별값 sid 생성
  → JWT 생성
  → Redis의 현재 로그인 정보 교체
  → 응답의 Set-Cookie로 JWT 설정
  → 로그인 결과 반환
```

### Security 클래스들은 왜 여러 개인가?

| 클래스 | 핵심 역할 |
| --- | --- |
| `CustomUserDetailsService` | DB에서 해당 아이디 회원을 찾아옴. 없으면 UsernameNotFoundException |
| `CustomUserDetails` | UserDto를 Spring Security가 이해하는 사용자 형식으로 감쌈 |
| `CustomAuthenticationProvider` | 비밀번호·실패 횟수·회원 상태를 검사해 인증 성공 여부 결정 |
| `JwtTokenProvider` | 인증 성공 후 사용할 토큰 생성·서명·검증 |
| `JwtAuthenticationFilter` | 이후 HTTP 요청에 실려 온 토큰을 검사 |

`CustomUserDetails`는 새로운 회원 테이블이나 JWT가 아니다. Spring Security의 `UserDetails` 인터페이스에 맞추는 어댑터다.
`getAuthorities()`는 DB의 USER를 `ROLE_USER`처럼 Security 권한 형식으로 바꾼다.
로그인에서는 AuthService가 커스텀 Provider를 직접 호출한다. 기본 폼 로그인 필터가 자동으로 로그인 요청을 처리하는 구성이 아니다.

### 회원 없음과 DB 장애를 왜 구분하나?

- 조회 성공·회원 없음: 없는 아이디로 로그인한 것이므로 로그인 실패로 처리한다.
- 회원 있음·비밀번호 불일치: 로그인 실패로 처리한다.
- DB 연결·SQL 오류: 인증 여부를 판단할 수 없는 서버 장애다. 실패 횟수로 세지 않는다.

Provider는 `UsernameNotFoundException`만 실패 횟수 처리로 넘긴다. 모든 DB 예외를 잡아 비밀번호 오류로 바꾸지 않는다.

## 8. JWT·Bearer·쿠키·Redis·SecurityContext 구분

| 이름 | 무엇인가 | 현재 프로젝트에서의 역할 |
| --- | --- | --- |
| JWT | 서명된 토큰 형식 | 회원 번호·로그인 식별값·만료 시각 등을 담은 인증 증표 |
| Bearer | Authorization 헤더의 전달 규약 | 백엔드는 지원하지만 현재 React는 이 헤더를 만들지 않음 |
| 쿠키 | 브라우저가 요청에 자동 전달하는 값 | accessToken·refreshToken JWT 전달 |
| Redis | 별도 서버인 키·값 저장소 | 현재 로그인 sid와 리프레시 토큰 jti 확인 |
| SecurityContext | 현재 요청의 인증 정보가 놓이는 곳 | Controller에서 인증된 사용자를 찾도록 함 |
| JSESSIONID·HttpSession | 서버 HTTP 세션 방식 | 현재 Security 인증 저장 방식으로 사용하지 않음 |

**Bearer에서 JWT로 바꾼다**는 표현은 맞지 않는다. JWT를 Bearer 헤더로 보낼 수도, 쿠키로 보낼 수도 있다.
현재 React는 JWT를 sessionStorage·localStorage·스토어에 보관하지 않고 기존 쿠키 인증을 사용한다.
로그인 API 응답 DTO에는 호환을 위한 토큰 필드가 여전히 있다. ‘백엔드가 토큰을 JSON으로 전혀 반환하지 않는다’는 뜻은 아니다.

### JWT에 들어가는 값

- `sub`: DB의 회원 고유 번호 userId
- `sid`: 현재 로그인을 구분하는 값
- `type`: access 또는 refresh
- `iat`, `exp`: 발급·만료 시각
- 리프레시 토큰의 `jti`: 개별 리프레시 토큰 식별값

JWT는 내용 전체를 비밀로 암호화하는 장치가 아니다. 서명으로 변경 여부를 검증한다.
따라서 비밀번호 같은 비밀정보를 payload에 넣으면 안 된다.
서명키는 토큰 위조 방지에 사용하며 유출되면 안 된다. 현재 Provider는 설정 문자열의 UTF-8 바이트를 서명키로 사용한다. 회원마다 다른 키나 매 요청 랜덤 키를 생성하는 구조가 아니다.

### 쿠키 속성

`AuthCookieServiceImpl`은 HttpOnly·Secure·Path=/·SameSite=Lax로 쿠키를 만든다.
HttpOnly는 프론트 JavaScript에서 쿠키 값을 직접 읽지 못하게 하는 속성이지, 브라우저가 서버로 보내지 못하게 하는 속성이 아니다.
Secure는 전송 환경과 관련된 속성이다. 운영 환경의 HTTPS·쿠키 정책은 별도 검토 대상이다.

### SecurityContext는 세션 스토리지인가?

아니다. 서버가 **지금 처리 중인 요청**에서 인증된 사용자 정보를 사용할 수 있게 두는 곳이다.
현재 설정은 STATELESS와 NullSecurityContextRepository를 사용하므로 HTTP 세션에 인증을 저장해 다음 요청에서 꺼내는 방식이 아니다.
다음 요청은 다시 JWT 필터가 검사한다.
다만 Redis에 로그인 상태를 기록하므로, 시스템 전체를 ‘서버 상태가 전혀 없는 JWT’라고 설명하는 것도 정확하지 않다.

## 9. 로그인 후 요청·새로고침 흐름

```text
브라우저가 JWT 쿠키를 실어 요청
  → JwtAuthenticationFilter
  → 토큰 서명·만료·종류 확인
  → JWT sid가 Redis의 현재 sid와 일치하는지 확인
  → DB 회원 조회·ACTIVE 상태 확인
  → CustomUserDetails로 Authentication 생성
  → 현재 요청의 SecurityContext에 넣음
  → Controller가 @AuthenticationPrincipal로 사용자 사용
```

현재 필터는 Bearer 헤더가 있으면 먼저 사용하고, 없으면 accessToken 쿠키를 읽는다.
프론트가 쿠키 인증으로 연결됐다고 해서 백엔드의 Bearer 지원 자체를 삭제한 것은 아니다.

### 새로고침해도 화면에 로그인 상태가 표시되는 이유

React가 `GET /api/v1/users/me`를 호출하면 브라우저가 기존 쿠키를 보낸다.
서버가 위 과정을 거쳐 회원 정보를 반환하면 화면을 표시한다. 저장소에 남긴 회원 정보를 읽어서 로그인이라고 판단하는 구조가 아니다.
확인 중에는 대기 화면을, 401이면 비로그인을, 서버 장애면 오류와 재시도를 표시한다.

### 공개 API는 왜 필터 검사를 생략하나?

가입·중복 확인·이메일 인증·계정 찾기는 로그인 없이 진행할 수 있다.
로그아웃된 예전 JWT가 쿠키에 남아 있다는 이유로 공개 절차까지 401로 막히지 않도록 `shouldNotFilter`에서 해당 경로를 생략한다.
JWT 검사 생략이 이메일 인증·비밀번호 조건 검사를 생략한다는 뜻은 아니다. 그 조건은 각 서비스에서 확인한다.

## 10. Redis·재발급·로그아웃

읽을 파일: [LoginSessionRepository](../back/src/main/java/com/kh/back/auth/security/LoginSessionRepository.java)

Redis Hash 키는 `LOGIN:user-id:{userId}`이고 값은 sid와 jti다.

```redis
HGETALL LOGIN:user-id:3
```

여기서 3은 로그인 아이디가 아니라 DB 회원 번호다. 결과가 비었다고 DB 회원이 없는 것은 아니다.
Redis에는 현재 JWT 원문이나 회원 전체 정보가 아니라 로그인 식별값을 저장한다.

### 왜 JWT가 있는데 Redis도 필요한가?

서명이 정상인 JWT는 만료 전까지 살아 있다. Redis의 현재 sid를 함께 확인하면 로그아웃하거나 새 로그인으로 교체된 토큰을 만료 전에도 거부할 수 있다.
회원별로 하나의 현재 로그인 키를 사용하므로 다른 기기에서 로그인하면 기존 로그인은 교체된다.

### 자동 로그인과 재발급

- `rememberMe=false`: 액세스 토큰만 발급하고 이전 리프레시 쿠키를 지운다. Redis TTL은 액세스 유효기간에 맞춘다.
- `rememberMe=true`: 리프레시 토큰도 발급하고 해당 jti와 더 긴 TTL을 Redis에 기록한다.
- 현재 프론트는 false로 요청한다. 자동 로그인 체크박스 연결은 아직 하지 않았다.

재발급은 리프레시 토큰의 서명·종류·계정 상태와 Redis의 sid·jti를 검사한다.
성공하면 같은 sid를 유지하면서 새 리프레시 토큰과 jti로 교체한다. 이전 jti를 다시 쓰면 재사용으로 판단해 해당 로그인 정보를 종료하는 처리가 있다.
명시적인 `/auth/tokens` API와 필터 내부 재발급 경로가 있다. 여러 요청의 동시 재발급까지 완전히 검증됐다는 뜻은 아니다.

### 왜 Redis 작업에 Lua 스크립트를 쓰나?

‘기존 jti 확인 → 새 jti 저장’을 따로 실행하면 사이에 다른 요청이 끼어들 수 있다.
Lua 스크립트로 확인·변경·만료 설정을 Redis에서 한 번에 처리한다.
스크립트 결과 SUCCESS·SESSION_ENDED·TOKEN_REUSED를 Java enum으로 해석한다.

### 로그아웃

1. 현재 요청 토큰에서 userId·sid를 확인한다.
2. Redis의 현재 sid와 같은 로그인인 경우에만 키를 삭제한다.
3. 액세스·리프레시 쿠키를 삭제하도록 응답한다.
4. 현재 요청의 SecurityContext도 비운다.

과거 토큰으로 로그아웃 요청했다고 더 최근의 새 로그인까지 지우지 않도록 sid를 비교한다.
계정 탈퇴·비밀번호 재설정처럼 계정의 현재 로그인을 강제로 종료할 때는 `revokeCurrentSession(userId)`를 사용한다.

Redis를 재시작하면 항상 데이터가 사라진다고 단정할 수 없다. 지속성 설정에 따라 다르며, 현재 설치 환경의 설정은 별도 확인이 필요하다.
반면 아래 Caffeine 실패 기록은 애플리케이션 내부 값이므로 백엔드 재시작 시 사라진다.

## 11. 아이디 찾기와 비밀번호 재설정

읽을 파일: [AccountLookupApiController](../back/src/main/java/com/kh/back/auth/controller/AccountLookupApiController.java)

### 아이디 찾기

1. 이름·이메일로 회원이 있는지 확인한 뒤 인증 메일을 보낸다.
2. 이름·이메일·코드로 아이디 찾기를 요청한다.
3. Controller에서 인증번호를 확인한다.
4. AuthService에서 인증 완료 기록을 확인하고 이름·이메일로 아이디를 조회한다.
5. 성공하면 인증 완료 기록을 제거하고 아이디를 반환한다.

현재 백엔드는 아이디 문자열을 반환하고, 화면 마스킹은 프론트가 담당한다.

### 비밀번호 재설정

1. loginId·email이 일치하는지 확인한 뒤 인증 메일을 보낸다.
2. 이메일 인증 API에서 번호를 확인한다.
3. loginId·email·newPassword로 재설정을 요청한다.
4. 새 비밀번호 조건, 회원과 이메일 일치, 인증 완료 기록을 검사한다.
5. 새 비밀번호를 BCrypt로 해시한다.
6. SQL에서도 loginId와 email을 조건으로 갱신하고 갱신 행이 1개인지 확인한다.
7. Redis의 현재 로그인을 종료하고 이메일 인증 완료 기록을 제거한다.

**다른 이메일을 인증했다고 아무 계정의 비밀번호를 바꿀 수 없어야 한다.**
이메일 인증과 계정 소유 일치 확인은 서로 다른 검사이므로 둘 다 필요하다.
갱신 행 수 검사는 조회 직후 이메일 등 계정 정보가 바뀌었을 때 성공으로 오인하지 않기 위한 장치다.

## 12. 회원정보 변경

읽을 파일: [UserProfileApiController](../back/src/main/java/com/kh/back/user/controller/UserProfileApiController.java)

요청 경로의 공통 기준은 `/api/v1/users/me`다. me는 현재 인증된 사용자라는 뜻이다.
Controller는 `@AuthenticationPrincipal CustomUserDetails`에서 수정 대상 loginId를 구한다.

| 기능 | 업무 순서 |
| --- | --- |
| 내 정보 조회 | 인증 사용자 번호 → 회원 조회 → 비밀번호 제외 응답 |
| 닉네임 변경 | 필수·최대 길이 검사 → UPDATE → 현재 요청 인증 정보 갱신 |
| 이메일 변경 | 필수·최대 길이·새 이메일 인증 확인 → UPDATE → 인증 정보 갱신 → 인증 기록 소비 |
| 비밀번호 변경 | 현재 비밀번호 대조 → 새 비밀번호가 기존과 같은지 확인 → 새 해시 저장. 실제 코드는 새 값 형식 검사를 먼저 실행 |
| 이미지 변경 | 새 파일 저장 → DB 경로 변경 → 성공 후 기존 파일 정리 |
| 이미지 삭제 | DB 경로를 null로 변경 → 커밋 후 파일 정리 |

`refreshSecurityContext`는 수정한 회원 정보를 **현재 요청의** 인증 객체에 다시 반영한다.
브라우저 sessionStorage를 갱신하거나 서버 HttpSession에 영구 저장하는 메서드가 아니다.
다음 요청에서는 JWT 필터가 DB의 최신 회원을 다시 조회한다.

마이페이지 비밀번호 변경과 계정 찾기 비밀번호 재설정은 인증 근거가 다르다.
전자는 현재 로그인과 현재 비밀번호를 사용하고, 후자는 계정·이메일과 이메일 인증을 사용한다.
현재 마이페이지 변경은 재설정과 달리 Redis 로그인 강제 종료를 호출하지 않는다.

## 13. 회원 탈퇴

1. 화면에서 즉시 삭제·복구 불가 경고를 확인하고 체크한 뒤 현재 비밀번호를 입력한다. 서버는 현재 로그인 사용자의 비밀번호를 확인한다.
2. `deleteUser`에서 실제 users 행을 DELETE한다.
3. Redis의 현재 로그인 키를 삭제한다.
4. JWT 쿠키를 지우고 현재 요청의 인증 정보를 비운다.

현재 사용자 정책은 즉시 탈퇴다. 새 탈퇴 처리는 DELETED 상태로 30일 보관하는 방식이 아니다.
탈퇴 대기 복구 API·분기·30일 정리 스케줄러와 회원 deletedAt 필드는 제거했다. 휴면 복구는 유지한다.
게시글 등 다른 테이블이 회원을 외래키로 참조하면 삭제가 거부될 수 있고, 공통 예외 처리에서 409로 응답한다.
사용자 삭제 뒤 기록 보존·익명화·외래키 정책은 아직 후속 설계 대상이다.

## 14. 파일 저장과 DB 트랜잭션

읽을 파일: [FileUtil](../back/src/main/java/com/kh/back/common/util/FileUtil.java)

- `MultipartFile`: HTTP로 받은 파일
- `SavedFile`: 원래 이름·저장 이름·웹 경로를 돌려주는 내부 결과 객체
- 실제 저장 위치: **백엔드 실행 작업 디렉터리 기준** `uploads` 하위
- 프로필의 DB 값: 파일 내용이 아니라 `/uploads/profile/파일명` 같은 경로
- 저장 이름: UUID와 확장자를 조합해 충돌을 줄임
- `WebMvcConfig`: `/uploads/**` 요청을 로컬 파일 폴더에 연결

중요한 점은 `uploads`가 항상 back 폴더 안이라는 보장이 없다는 것이다. 실행 작업 디렉터리가 프로젝트 루트이면 루트의 uploads가 사용된다.

### 파일은 DB 롤백으로 자동 복구되지 않는다

파일 저장은 디스크 작업이고 DB UPDATE는 DB 트랜잭션이다.
FileUtil은 Spring의 트랜잭션 완료 콜백을 이용해 다음처럼 정리한다.

| 결과 | 처리 |
| --- | --- |
| 파일 교체·DB 커밋 성공 | 기존 파일 삭제 |
| 새 파일 저장 후 DB 롤백 | 새 파일 삭제, 기존 파일 유지 |
| 이미지 삭제·DB 커밋 성공 | 기존 파일 삭제 |
| 커밋 후 파일 삭제 실패 | DB를 되돌릴 수 없으므로 로그 기록 |

DB와 파일 시스템이 하나의 원자적 트랜잭션이 된 것은 아니다. 실패 시 불일치를 줄이기 위한 보완 처리다.
이 장에서는 회원 프로필 파일 처리에 필요한 내용만 다룬다.

## 15. 응답·예외·검증·트랜잭션

### 공통 응답 ApiResponse

```json
{
  "success": true,
  "data": { "userId": 3, "loginId": "sample_user" },
  "meta": null,
  "error": null
}
```

`ApiResponse<T>`의 T는 data 타입이다. 회원이면 UserDto, 중복 여부면 Boolean, 처리 결과만 필요하면 Void를 쓴다.
제네릭으로 같은 응답 틀에 서로 다른 결과 타입을 넣는다.
meta는 페이지 정보이고, 실패 시 error에 code·message·필요하면 fields를 넣는다.

### InputValidator와 PasswordValidator

InputValidator는 필수값·길이·필드별 오류 형식을 통일한다.
PasswordValidator는 새 비밀번호 업무 규칙을 통일한다. 검증 실패는 400 VALIDATION_ERROR와 필드별 메시지로 전달한다.
프론트 검증은 입력 편의를 위해, 백엔드 검증은 직접 API 요청을 해도 규칙이 지켜지도록 하기 위해 필요하다.

### 예외는 어디에서 응답이 되나?

Service는 ApiException으로 상태·오류 코드·설명을 전달하고, Controller 예외는 GlobalExceptionHandler가 공통 JSON으로 바꾼다.
예상하지 못한 오류의 내부 원인은 서버 로그에 남기고 응답에는 일반 메시지를 준다.
보안 필터는 Controller보다 앞이므로, 필터·인증 진입점에서의 응답은 ApiResponseWriter 등 별도 경로로 작성한다.
모든 예외가 무조건 ControllerAdvice를 통과하는 것은 아니다.

| HTTP 상태 | 의미·예 |
| --- | --- |
| 400 | 입력값·인증번호·현재 비밀번호 오류 |
| 401 | 로그인 필요·잘못된 인증·로그인 종료 |
| 403 | 인증돼도 접근 권한 없음 |
| 409 | 중복·다른 데이터 참조·조회 후 변경 충돌 |
| 503 | 인증 메일 발송 불가 |
| 500 | 예상하지 못한 DB·서버 오류 등 |

### @Transactional은 왜 쓰나?

메서드 안의 DB 작업을 성공 시 커밋하고, 정해진 예외가 발생하면 롤백하도록 묶는다.
조회 중심 서비스는 클래스에 readOnly=true를 두고 쓰기 메서드에 일반 트랜잭션을 지정한다.
readOnly는 읽기 의도와 최적화 힌트이며 모든 쓰기를 물리적으로 막는 보안 장치라고 설명하지 않는다.
기본적으로 런타임 예외는 롤백 대상이다. checked 예외는 설정에 따라 다르다.
**Redis·Caffeine·메일 발송·디스크 파일까지 DB 트랜잭션으로 자동 롤백되는 것은 아니다.**

## 16. 현재 한계: 완료로 발표하지 않을 부분

| 항목 | 현재 코드에서 확인한 사실 | 설명할 때 주의 |
| --- | --- | --- |
| 자동 로그인 화면 | rememberMe는 항상 false로 요청 | 백엔드의 리프레시 지원과 화면 연결 완료를 구분 |
| 로그인 실패 기록 | Caffeine에 1시간, 일시 차단은 서버 메모리 Map | Redis에 저장한다고 설명하지 않기 |
| 실패 누적 정책 | 5회 30분, 10회 60분, 15회 DB 잠금 의도 | 60분 차단 동안 실패 기록이 만료될 수 있음 |
| 15회 잠금 트랜잭션 | login의 DB 트랜잭션 안에서 잠금 UPDATE 후 예외 발생 | 잠금 변경이 롤백될 구조이며 실제 DB 검증·수정 필요 |
| 실패 횟수 동시성 | 캐시 읽기와 증가·쓰기가 분리됨 | 동시 요청에서도 정확한 누적이라고 단정하지 않기 |
| 실패 안내 | Provider의 자세한 문구가 공통 핸들러에서 일반 인증 오류로 바뀜 | 실패 횟수·남은 분이 화면에 항상 전달된다고 말하지 않기 |
| 해제 메일 링크 | /auth/unlock-account로 이동, 구현 API는 POST /api/v1/auth/account-unlocks | 실제 링크→화면→해제 연결이 남음 |
| 휴면 스케줄러 | @Scheduled 코드는 있으나 @EnableScheduling은 현재 Java 코드에 없음 | 자동 실행 중이라고 말하지 않기 |
| 스케줄 시각 | 주석은 새벽 3시지만 기본 cron은 10초 간격 | 주석만 읽고 동작을 설명하지 않기 |
| 이메일 인증 | 이메일을 키로 공용 캐시, Math.random 생성 | 용도별 분리·보안 난수·횟수 제한 등 추가 검토 여지 |
| 설정 비밀번호 변경 | 새 해시 저장, Redis 강제 종료 없음 | 계정 찾기 재설정과 동일한 세션 종료라고 말하지 않기 |
| 인증 설정 범위 | 명시적 authenticated 경로는 /users/me와 하위, 나머지 permitAll | 모든 API가 로그인 필수라고 말하지 않기 |
| CSRF·CORS | 현재 SecurityConfig에서는 비활성화 | 쿠키 인증의 운영 보안·배포 출처 정책은 추가 검토 필요 |
| 로그인 실패 안내 | 미연결 CustomLoginFailureHandler는 삭제함 | 현재 로그인 오류는 GlobalExceptionHandler의 JSON 응답을 화면에 표시 |
| 파일·캐시·메일 | DB와 별도 자원 | @Transactional이 모든 부수 효과를 되돌린다고 설명하지 않기 |

이 표는 이번 문서에서 추가로 수정했다는 뜻이 아니다. **현재 구현을 정직하게 설명하기 위한 경계**다.

## 17. 코드리뷰 예상 질문과 답변

**Q. JWT를 쓰는데 Redis가 왜 필요한가요?**

A. JWT의 서명·만료만 보면 로그아웃된 토큰도 만료 전에는 유효할 수 있습니다. 현재 로그인 sid를 Redis와 비교해 종료·교체된 로그인을 거부합니다.

**Q. JWT 방식인데 세션이라는 단어를 왜 쓰나요?**

A. 여기서 sid·LoginSessionRepository는 한 번의 로그인을 식별·관리하는 뜻입니다. HttpSession·JSESSIONID로 인증을 유지하는 방식과 다릅니다.

**Q. JWT를 복호화해서 회원을 확인하나요?**

A. 서명을 검증한 뒤 claim에서 회원 번호를 읽습니다. payload의 내용을 비밀 암호화하는 개념이 아니므로 비밀번호를 넣지 않습니다.

**Q. 가입할 때 인증번호를 화면에서 검사하면 충분하지 않나요?**

A. 화면 검사는 우회할 수 있습니다. 서버가 인증번호를 확인하고 인증 완료 기록을 만든 뒤, 가입 서비스가 해당 이메일의 완료 기록을 다시 검사합니다.

**Q. DTO를 왜 여러 개 만들었나요?**

A. 요청별 필요한 데이터와 응답에 공개할 정보가 다릅니다. 비밀번호가 있는 요청 객체를 그대로 응답하거나, 역할·상태까지 수정 요청으로 받지 않도록 분리합니다. 가입 DTO 재사용 등 남은 개선점도 있습니다.

**Q. 아이디 중복 확인 API가 있는데 UNIQUE가 왜 필요한가요?**

A. 확인 이후 실제 저장 전까지 다른 요청이 같은 값을 사용할 수 있습니다. API는 안내, DB 제약은 최종 무결성 보장입니다.

**Q. DB 장애면 비밀번호 실패 횟수도 증가하나요?**

A. 회원 없음만 로그인 실패로 계산하고 DB 조회 장애는 그대로 전달합니다. 서버 장애를 사용자 비밀번호 오류로 계산하지 않도록 구분했습니다.

**Q. 프로필을 바꾸는 중 DB 저장이 실패하면 기존 사진은요?**

A. 새 파일은 롤백 시 정리하고 기존 파일은 커밋 후 삭제합니다. 다만 디스크와 DB가 단일 트랜잭션인 것은 아닙니다.

**Q. 새로고침 때 로그인은 어떻게 유지되나요?**

A. 브라우저가 JWT 쿠키를 보내고 프론트가 내 정보 API를 조회합니다. 서버가 JWT·Redis·회원 상태를 검사한 결과를 화면에 반영합니다. 세션 스토리지에서 토큰을 꺼내지 않습니다.

## 18. 발표할 때 읽을 수 있는 요약

### 1분 설명

> 백엔드는 회원 데이터를 관리하는 user, 인증을 담당하는 auth, 공통 보안·응답·파일 처리를 담당하는 common으로 나뉩니다. 요청은 Controller에서 받아 Service에서 업무 규칙을 처리하고 Mapper와 XML을 통해 MySQL을 조회·갱신합니다. DTO는 각 요청과 응답에 필요한 데이터만 전달하기 위해 나눴습니다.
>
> 회원가입은 서버의 이메일 인증 완료 기록을 확인하고 비밀번호를 BCrypt로 해시한 뒤 저장합니다. 로그인은 DB 회원과 비밀번호·상태를 확인한 후 JWT를 쿠키에 설정하고 Redis에 현재 로그인 식별값을 기록합니다. 이후 요청은 JWT 필터가 토큰과 Redis 상태를 확인해 현재 요청의 인증 사용자 정보를 구성합니다.
>
> 이메일 인증은 서버 내부 Caffeine 캐시를 사용하며, 계정 찾기와 비밀번호 재설정은 인증 이메일이 실제 계정과 일치하는지도 확인합니다. 현재 로그인 실패 누적·잠금 해제 화면 등은 후속 정리가 필요합니다.

### 마지막으로 구분할 말

- 비밀번호 **해시**와 JWT **서명**은 다르다.
- JWT **형식**과 Bearer·쿠키 **전달 방식**은 다르다.
- DB 회원 정보, Redis 현재 로그인, Caffeine 임시 인증 정보는 서로 다르다.
- DTO 파일과 DB 테이블은 다르다.
- 현재 요청의 SecurityContext와 브라우저 sessionStorage는 다르다.
- 구현된 코드가 있다는 사실과 실제 화면·DB 검증이 끝났다는 사실은 다르다.

## 19. 코드 위치 빠른 찾기

| 공부 주제 | 파일 |
| --- | --- |
| 보안 진입점·접근 설정 | [SecurityConfig](../back/src/main/java/com/kh/back/common/config/SecurityConfig.java) |
| 요청마다 JWT 확인 | [JwtAuthenticationFilter](../back/src/main/java/com/kh/back/common/security/JwtAuthenticationFilter.java) |
| JWT 생성·검증 | [JwtTokenProvider](../back/src/main/java/com/kh/back/common/security/JwtTokenProvider.java) |
| JWT 쿠키 속성·삭제 | [AuthCookieServiceImpl](../back/src/main/java/com/kh/back/auth/service/AuthCookieServiceImpl.java) |
| Redis 현재 로그인 | [LoginSessionRepository](../back/src/main/java/com/kh/back/auth/security/LoginSessionRepository.java) |
| 로그인·계정 찾기·재발급 | [AuthServiceImpl](../back/src/main/java/com/kh/back/auth/service/AuthServiceImpl.java) |
| 실패 횟수·상태 검사 | [CustomAuthenticationProvider](../back/src/main/java/com/kh/back/auth/security/CustomAuthenticationProvider.java) |
| 실패 횟수 저장 | [LoginAttemptServiceImpl](../back/src/main/java/com/kh/back/auth/security/LoginAttemptServiceImpl.java) |
| 이메일 인증 | [EmailVerificationServiceImpl](../back/src/main/java/com/kh/back/auth/service/EmailVerificationServiceImpl.java) |
| 회원 업무 | [UserServiceImpl](../back/src/main/java/com/kh/back/user/service/UserServiceImpl.java) |
| DB 회원 SQL | [UserMapper.xml](../back/src/main/resources/mappers/user/UserMapper.xml) |
| 공통 응답·오류 | [ApiResponse](../back/src/main/java/com/kh/back/common/dto/ApiResponse.java), [GlobalExceptionHandler](../back/src/main/java/com/kh/back/common/exception/GlobalExceptionHandler.java) |
| 파일과 DB 정리 | [FileUtil](../back/src/main/java/com/kh/back/common/util/FileUtil.java) |

문서만으로 외우기보다, 설명하려는 기능의 Service 메서드 한 개를 열고 이 순서와 연결해 읽으면 된다.
