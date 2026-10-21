# 인증·인가 역할 분담과 현재 사용자 정보 사용 정리

작성일: 2026-10-21

이 문서는 인증·인가 담당자가 현재 구조를 이해하고, 기능 담당 조원에게 사용 방법을 설명하기 위한 학습·협업 자료다. 현재 코드를 확인한 내용과 앞으로 합의할 개선안을 구분한다. 이 문서를 작성하면서 애플리케이션 코드는 변경하지 않았다.

## 1. 왜 이 이야기를 시작했는가

인증·인가 담당자가 따로 있는데도 게시판·채팅 등 다른 담당자의 코드에 로그인 확인과 `principal == null`, `userId == null` 검사가 반복되어 있었다. 무엇을 공통 인증 코드에서 책임지고 무엇을 기능 코드에서 검사해야 하는지 이해하기 위해 논의했다.

중요한 점은 **다른 조원의 코드에 사용자 확인 코드가 있다고 전부 잘못된 것은 아니라는 것**이다.

| 기능 코드에 있는 처리 | 판단 |
| --- | --- |
| JWT를 꺼내 서명·만료를 직접 검증 | 공통 인증에서 처리할 일을 중복 구현한 것 |
| 로그인 여부를 기능마다 따로 판단해 401 반환 | 중앙 접근 정책으로 모을 수 있는 부분. 현재 설정 확인 후 이전해야 함 |
| `@AuthenticationPrincipal`로 회원번호를 받음 | 이미 인증된 사용자 정보를 사용하는 정상적인 방법 |
| 글 작성자 ID와 현재 사용자 ID를 비교 | 해당 데이터의 소유권을 검사하는 인가. 기능 서비스에 필요 |
| 메시지가 비어 있는지, 금액이 올바른지 검사 | 업무 입력 검증. 기능 담당의 역할 |

따라서 목표는 기능 담당자의 사용자 관련 코드를 전부 없애는 것이 아니라, **로그인 검증의 중복을 줄이고 사용자 정보 사용·데이터 권한 검사는 명확하게 남기는 것**이다.

## 2. 논의한 네 가지 질문과 진행 상태

| 질문 | 현재까지 정리한 내용 |
| --- | --- |
| 1. 액세스 토큰을 어떻게 검증하는가 | JWT 필터 → 서명·만료·종류 확인 → Redis 세션 확인 → DB 회원 상태 확인 |
| 2. 인증된 회원정보를 기능 담당자가 어떻게 사용하는가 | `CustomUserDetails`를 `@AuthenticationPrincipal`로 받아 필요한 값만 서비스에 전달 |
| 3. 기능 담당자가 매번 null 검사를 해야 하는가 | 아직 팀 정책 확정 전. 현재의 검사가 필요한 이유와 중앙 보호로 옮기는 방향을 아래에 정리 |
| 4. 조원에게 어떤 규칙으로 전달하는가 | 아래 안내문은 공유 초안. API 분류와 적용 방법을 합의한 뒤 확정 |

## 3. 현재 프로젝트의 인증 구조

- JWT는 토큰의 형식이다.
- HttpOnly 쿠키는 브라우저가 JWT를 보관하고 서버에 보내는 수단이다.
- Redis는 현재 유효한 로그인 세션을 확인하는 서버 측 저장소다.
- Zustand는 화면에서 쓰는 회원정보와 로그인 상태를 메모리에 관리한다. 서버의 권한 판단 근거는 아니다.

JWT와 쿠키는 서로 반대 방식이 아니다. 현재 프로젝트는 **JWT를 쿠키로 전달하고, Redis 상태도 확인하는 구조**다. JWT만으로 모든 판단을 끝내는 완전한 무상태 구조는 아니다.

### 일반 로그인과 자동 로그인

`back/src/main/java/com/kh/back/auth/service/AuthServiceImpl.java`의 `login()` 기준이다.

| 항목 | 일반 로그인 | 자동 로그인 |
| --- | --- | --- |
| 액세스 토큰 | 발급 | 발급 |
| 로그인 식별번호 `sid` | 생성 | 생성 |
| 리프레시 토큰 | 발급하지 않음 | 발급 |
| Redis 로그인 기록 | 저장 | 저장 |
| Redis 유지 시간 | 액세스 토큰 유효시간 기준 | 리프레시 토큰 유효시간 기준 |

일반 로그인도 `sid`가 있다. 리프레시 토큰의 식별자 `jti`만 없고, Redis에는 빈 값으로 저장한다. 토큰 검증과 Redis 조회는 모두 서버에서 실행한다.

## 4. 질문 1: 액세스 토큰은 어디서 검증하는가

주요 파일:

- `back/src/main/java/com/kh/back/common/security/JwtAuthenticationFilter.java`
- `back/src/main/java/com/kh/back/common/security/JwtTokenProvider.java`
- `back/src/main/java/com/kh/back/auth/security/LoginSessionRepository.java`

공개 인증 절차 등 필터 제외 경로를 제외하면, 요청은 컨트롤러에 도착하기 전에 다음 흐름을 거친다.

```text
요청에서 토큰 추출
→ JWT 서명·만료·형식과 회원번호 검사
→ access 토큰인지 확인
→ JWT의 sid와 Redis의 현재 sid 비교
→ DB에서 회원정보 조회 및 ACTIVE 상태 확인
→ Spring Security에 인증 사용자 등록
→ 해당 API 접근 정책 확인
→ 컨트롤러 실행
```

현재 `resolveAccessToken()`은 Bearer 헤더가 있으면 우선 사용하고, 없으면 `accessToken` 쿠키를 읽는다. 프론트는 쿠키 방식이지만 백엔드는 Bearer 헤더도 지원한다.

JWT 검증의 핵심 코드는 다음과 같다.

```java
Jwts.parser()
        .verifyWith(secretKey)
        .build()
        .parseSignedClaims(token);
```

검증된 JWT라도 Redis의 현재 로그인과 `sid`가 다르면 이전 로그인으로 판단해 거절한다. 액세스 토큰이 없거나 검증에 실패했고 리프레시 쿠키가 있다면, `AuthServiceImpl.reissue()`에서 별도 검증·교체 후 인증을 이어가는 분기도 있다.

**토큰이 없다는 이유만으로 모든 요청을 필터에서 일괄 거절하는 구조는 아니다.** 공개 API는 비로그인 접근이 필요하므로, 로그인 필수 여부는 `SecurityConfig`의 접근 정책과 구분해야 한다.

## 5. 질문 2: 인증 후 회원정보는 어디에 있는가

### 세션 방식과 현재 JWT 방식의 차이

| 구분 | 일반적인 서버 세션 방식 | 현재 JWT 방식 |
| --- | --- | --- |
| 브라우저가 전달하는 값 | 세션 ID | JWT |
| 서버가 요청자를 식별하는 방법 | ID에 해당하는 서버 세션 확인 | JWT 검증 및 Redis의 현재 sid 확인 |
| 회원정보 확보 | 세션의 회원번호·정보 사용, 필요하면 DB 조회 | JWT의 회원번호로 DB 조회 |
| 기능 코드에서 사용하는 인증 사용자 | Spring Security에 등록해 사용 가능 | Spring Security에 등록해 사용 |

세션 방식도 회원정보를 매번 DB에서 조회할 수 있고, JWT 방식도 토큰에 모든 개인정보를 담을 필요가 없다. 현재 프로젝트는 JWT에서 회원번호를 얻어 DB를 조회한다.

### CustomUserDetails의 역할

`back/src/main/java/com/kh/back/auth/security/CustomUserDetails.java`는 현재 `UserDto`를 감싼다.

```java
private final UserDto userDto;

public Long getUserId() {
    return userDto.getUserId();
}
```

따라서 조회된 이름·이메일·생년월일·닉네임·역할·상태 등에도 서버 코드에서 접근할 수 있다. DB의 게시글·채팅·거래내역까지 들어 있는 것은 아니다.

현재 JWT 필터가 사용하는 `UserServiceImpl.getUserById()`는 회원을 조회한 후 `password`를 `null`로 비운다. 다만 `UserDto` 자체에는 비밀번호 필드가 있고 로그인 인증 단계의 객체와도 함께 쓰이므로, 모든 시점의 인증 객체에 비밀번호가 없다고 일반화하면 안 된다.

필터는 이 객체를 이번 요청의 `SecurityContext`에 등록한다. 세션에 회원정보 전체를 영구 보관하거나, 브라우저로 자동 전송하는 의미가 아니다.

### 기능 담당자가 받는 방법

로그인 필수로 보호된 API에서는 다음처럼 받는다. 아래는 설명용 예시이며 현재 API를 바꾼 코드가 아니다.

```java
@PostMapping("/messages")
public ApiResponse<ChatMessageDto> sendMessage(
        @RequestBody SendMessageRequest request,
        @AuthenticationPrincipal CustomUserDetails principal
) {
    // 요청 본문의 작성자 ID가 아니라 서버가 검증한 회원번호를 사용한다.
    Long userId = principal.getUserId();
    return ApiResponse.ok(chatService.sendMessageAsMember(userId, request.content()));
}
```

현재 구조에서 닉네임이 필요하면 `principal.getUserDto().getNickname()`으로 읽을 수 있다. 단, 닉네임을 권한 판단의 식별자로 사용하지 않는다.

서비스에는 필요한 `userId`나 닉네임을 명시적으로 전달한다. 서비스가 JWT를 다시 해석하거나 `SecurityContext`를 여기저기 직접 조회하게 만들지 않는 방향이 이해와 테스트에 유리하다.

### 회원 데이터는 어떤 기준으로 가져와 사용하는가

인증된 회원정보는 프론트에서 받아 서버에 다시 넘기는 것이 아니라, 서버 내부의 principal에서 읽는다. 서버 컨트롤러가 자신의 `/users/me` API를 다시 호출할 필요도 없다.

```java
// 현재 인증 객체에서 필요한 값만 읽는 예시다.
Long userId = principal.getUserId();
String loginId = principal.getUsername();
String nickname = principal.getUserDto().getNickname();
String userRole = principal.getUserDto().getUserRole();
```

| 필요한 데이터 | 사용하는 방법 |
| --- | --- |
| 현재 로그인한 사람의 회원번호 | principal.getUserId() |
| 현재 구조의 로그인 ID·닉네임 | principal.getUsername(), principal.getUserDto().getNickname() |
| 글 내용·작성자 ID·상담방 소유자 | 해당 기능의 Mapper로 DB 조회 |
| 본인 글·본인 상담방인지 | DB 소유자 ID와 인증된 userId 비교 |
| 프론트에 보여줄 회원정보 | 필요한 필드만 응답 DTO로 구성 |

예를 들어 게시글 작성에서는 컨트롤러가 `principal.getUserId()`를 서비스에 전달하고, 서비스가 그 번호를 작성자로 저장한다. 반대로 게시글 수정에서는 수정 대상 글을 DB에서 조회하고 소유권을 확인한다. 로그인 회원의 프로필이 있다고 게시글 데이터까지 principal에 들어 있는 것은 아니다.

여기에서 읽은 회원정보는 요청 인증 과정에서 조회한 값이다. 요청 도중 상태 변경이 경합하거나 결제·중요 상태 변경 등 최신 값과 잠금이 필요한 업무는 기능 트랜잭션에서 필요한 데이터를 다시 확인할 수 있다. '회원정보는 어떤 경우에도 재조회하면 안 된다'는 규칙은 아니다.

**주의:** 위 예시처럼 null 검사를 생략하려면 해당 API에 로그인 필수 접근 정책이 먼저 적용되어 있어야 한다. 현재 모든 API가 그렇게 보호되는 것은 아니다.

### 실제 사용처: 이미 principal을 받고 있는 기능

아래는 2026-10-21 현재 코드의 사용처다. 인증 담당자가 사용자 객체를 만들어 요청에 등록하고, 각 기능 컨트롤러가 필요한 값을 꺼내 사용한다.

| 기능 | 파일 (back/src/main/java/com/kh/back/ 기준) | 사용하는 정보와 목적 |
| --- | --- | --- |
| 게시글 등록·수정 | post/controller/PostApiController.java | 회원번호를 작성자로 사용하고 닉네임을 전달 |
| 회원 상담 | chat/controller/ChatApiController.java | requireUserId()로 회원번호를 받아 방·메시지 서비스에 전달 |
| 관리자 상담 | chat/controller/AdminChatApiController.java | 인증된 관리자 회원번호를 상담 처리에 전달. 관리자 접근 제한은 SecurityConfig와 함께 확인 |
| 내 알림 | notification/controller/NotificationApiController.java | 현재 회원의 목록·미읽음 수 조회와 읽음 처리 |
| 내 정보 | user/controller/UserProfileApiController.java | 회원번호·로그인 ID로 본인 정보 조회·변경 |
| PDF 업로드·거래 저장 | TaxUpload/controller/YearEndTaxController.java | 비로그인은 null, 로그인 회원은 인증된 회원번호를 서비스에 전달 |

#### 예시 A: 게시글 작성자로 사용하기

`PostApiController.createPost()`에서는 앞부분에서 principal의 null 여부를 검사한 뒤 아래와 같이 사용한다. 코드 일부를 발췌했다.

```java
PostDto postDto = new PostDto();
// 작성자 번호는 요청 본문 대신 인증된 사용자에서 가져온다.
postDto.setUserId(principal.getUserId());
postDto.setTitle(title);
postDto.setContent(content);
postDto.setBoardType(boardType);
postDto.setPublic(isPublic);
postDto.setWriter(principal.getUserDto().getNickname());

postService.saveWithFiles(postDto, savedFileNames);
```

이때 `setUserId()`는 로그인 검증을 다시 하는 코드가 아니다. **인증 담당이 검증한 사용자를 새 게시글의 작성자로 사용하는 코드**다. 프론트가 작성자 번호를 임의로 보내더라도 그 번호를 작성자로 저장하지 않는다.

#### 예시 B: 현재 회원의 알림만 조회하기

`NotificationApiController.getNotifications()`의 현재 구현이다.

```java
@GetMapping
public ApiResponse<List<NotificationDto>> getNotifications(
        @AuthenticationPrincipal CustomUserDetails principal
) {
    if (principal == null || principal.getUserId() == null) {
        throw new ApiException(HttpStatus.UNAUTHORIZED,
                "AUTHENTICATION_REQUIRED", "로그인이 필요합니다.");
    }

    return ApiResponse.ok(
            notificationService.getNotifications(principal.getUserId())
    );
}
```

호출 흐름은 다음과 같다.

```text
인증 필터가 사용자 등록
→ Spring이 @AuthenticationPrincipal 매개변수에 CustomUserDetails 주입
→ 컨트롤러가 회원번호 추출
→ 서비스에 회원번호 전달
→ Mapper가 해당 회원의 알림만 조회
```

여기서 알림 목록은 principal에 들어 있는 것이 아니다. principal의 회원번호를 조회 조건으로 사용해 기능 DB 데이터를 가져오는 것이다.

#### 예시 C: 공개·회원 겸용 PDF 업로드

`YearEndTaxController`에서는 비회원 체험도 지원하므로 다음처럼 구분한다.

```java
// 비회원 체험을 허용하는 API이므로 비로그인 상태도 의도적으로 처리한다.
Long userId = principal == null ? null : principal.getUserId();
return ApiResponse.ok(yearEndTaxService.uploadPdf(file, userId, taxYear));
```

이 null 처리는 불필요한 중복 인증이라고 볼 수 없다. 공개 API의 회원·비회원 동작을 구분하는 코드다. 로그인 필수 API와 일괄적으로 같은 방식으로 변경하면 안 된다.

#### 새 기능을 만드는 조원이 따라갈 순서

1. API가 공개인지, 로그인 필수인지, 관리자 전용인지 인증 담당자와 먼저 정한다.
2. 로그인 필수·관리자 전용이면 공통 접근 정책에 실제 경로와 메서드가 반영됐는지 확인한다.
3. 컨트롤러에서 `@AuthenticationPrincipal CustomUserDetails principal`을 받는다.
4. `getUserId()` 등 필요한 값만 꺼내 기능 서비스에 전달한다.
5. 서비스에서 게시글·방 등의 데이터를 조회하고, 현재 사용자에게 그 데이터의 권한이 있는지 검사한다.
6. 브라우저에는 필요한 결과 DTO만 반환한다. principal 전체를 그대로 반환하지 않는다.

현재 컨트롤러의 로그인 null 검사는 접근 정책 이전 전까지 유지한다. **principal을 사용하는 코드와 로그인 null 검사를 같은 것으로 보고 함께 삭제하지 않는다.**

## 6. 서버 인증 정보와 브라우저 응답은 다르다

| 구분 | 보관·전달하는 내용 |
| --- | --- |
| JWT | 회원번호, sid, 토큰 종류, 발급·만료 정보 등 |
| CustomUserDetails | 서버가 조회한 회원정보를 이번 요청에서 사용 |
| API 응답 DTO | 브라우저에 전달할 필드 |
| Zustand | 화면이 사용하는 메모리 상태 |
| Local Storage·Session Storage | 별도 저장 코드가 있어야 값이 남는 브라우저 저장소 |

`CustomUserDetails`에서 정보를 읽는다고 프론트에 전달되는 것은 아니다. 컨트롤러가 응답으로 반환해야 전달된다. 브라우저에 보낸 응답은 개발자도구에서 볼 수 있으며, 필요한 필드만 전달하는 것이 응답 최소화다.

현재 회원정보 조회 경로는 `/api/v1/auth/me`가 아니라 **`GET /api/v1/users/me`**다.

`UserProfileApiController.getUser()`는 principal에서 회원번호를 얻고 회원정보를 다시 조회해 `UserDto`를 반환한다. 따라서 이름·이메일·생년월일 등도 응답에 포함된다. 비밀번호는 `UserDto`의 `WRITE_ONLY` 설정으로 응답에서 제외된다.

응답을 줄이고 싶다면 전용 응답 DTO를 만들면 된다. `CustomUserDetails`를 반드시 함께 바꿀 필요는 없다.

```java
public record CurrentUserResponseDto(
        Long userId,
        String nickname,
        String userRole
) {}
```

위 필드 구성은 설명용 제안이며 확정 계약이 아니다. 현재 `/users/me`를 로그인 복원과 마이페이지에서 사용하므로, 응답을 줄이기 전 사용처와 상세정보 조회 분리를 함께 확인해야 한다.

또한 채팅 mock이 Local Storage에 상담 내용을 저장하는 문제는 `getCurrentUser()`가 아니라 `useChatStore.js`의 저장·복원 코드 문제다. 회원정보 응답 최소화와 채팅 저장 방식 변경은 독립적으로 진행할 수 있다.

## 7. 질문 3: 다른 담당자가 null 검사를 반복하는 이유

현재 `back/src/main/java/com/kh/back/common/config/SecurityConfig.java`의 핵심 규칙은 다음과 같다.

```java
.requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
.requestMatchers("/api/v1/users/me", "/api/v1/users/me/**").authenticated()
.anyRequest().permitAll()
```

관리자 API와 내 정보 API 외에는 기본적으로 접근을 허용한다. 그래서 게시글 작성·회원용 채팅 API는 컨트롤러까지 비로그인 요청이 들어올 수 있고, 다음 검사가 필요해진다.

### SecurityConfig에서 구분하는 접근 자격도 인가다

| 설정 | 뜻 | 현재 적용 범위 |
| --- | --- | --- |
| permitAll() | 로그인하지 않아도 접근 가능 | 나머지 경로의 기본 정책 |
| authenticated() | 인증된 사용자만 접근 가능 | /api/v1/users/me 및 그 하위 경로 |
| hasRole("ADMIN") | 관리자 권한이 있는 사용자만 접근 가능 | /api/v1/admin/** |

`authenticated()`는 '일반 회원만 허용'이 아니다. 관리자도 인증된 사용자라면 포함될 수 있다. 관리자만 쓰는 기능, 로그인 사용자 공통 기능, 비로그인도 쓰는 기능을 먼저 구분해야 한다.

이 설정은 API 입구의 접근 자격을 결정하는 인가이고, '이 사람이 누구인가'를 확인하는 JWT 인증과 역할이 다르다. 다만 글·상담방 등 데이터별 소유권 인가까지 URL 설정만으로 해결되는 것은 아니다. 관리자가 타인의 글도 삭제 가능한지 같은 업무 정책은 기능 담당자와 합의해 서비스에 적용한다.

현재 게시판·댓글·회원 채팅 등 전체 기능의 로그인 필수 분류가 중앙 설정에 완성된 상태는 아니다. 그래서 아래와 같은 컨트롤러 검사가 아직 남아 있다.

```java
if (principal == null || principal.getUserId() == null) {
    throw new ApiException(
            HttpStatus.UNAUTHORIZED,
            "AUTHENTICATION_REQUIRED",
            "로그인이 필요합니다."
    );
}
```

**현재 검사만 보고 기능 담당자가 인증을 잘못 구현했다고 판단하면 안 된다. 지금 상태에서 검사만 지우면 인증을 우회하거나 null 참조 오류가 날 수 있다.** `@AuthenticationPrincipal`도 사용자 주입을 위한 것이지 로그인 필수 접근 정책을 자동으로 만드는 것은 아니다.

### 앞으로 합의할 방향: 로그인 필수 API는 공통 설정에서 보호

예를 들어 채팅의 회원 전용 API를 다음처럼 보호할 수 있다. 아래는 제안이며 실제 적용하지 않았다.

```java
.requestMatchers("/api/v1/chat/**").authenticated()
```

이 규칙이 올바르게 적용된 로그인 필수 API라면 비로그인은 컨트롤러 전에 공통 401 응답을 받는다. 해당 전제를 검증한 뒤 컨트롤러의 반복적인 로그인 null 검사를 줄일 수 있다.

게시판은 읽기는 공개이고 작성·수정·삭제는 로그인 필수일 수 있으므로 HTTP 메서드와 실제 경로별로 구분해야 한다. 현재 게시판에는 `/api/posts` 경로도 있으므로 `/api/v1/**`만 설정하고 끝내면 누락될 수 있다. 관리자 전용 규칙보다 넓은 허용 규칙을 앞에 두지 않도록 순서도 확인한다.

### 그래도 기능 서비스에 남겨야 하는 검사

- 수정하려는 글의 작성자와 현재 회원번호가 같은가?
- 이 회원이 해당 상담방의 소유자인가?
- 비공개 자료를 볼 수 있는 대상인가?

이것은 로그인 여부가 아니라 **특정 데이터에 대한 권한 검사**다. 로그인한 사람이라도 남의 글을 수정할 수는 없다. 단순히 “인가 담당이 따로 있다”는 이유로 기능 서비스의 소유권 검사까지 없애지 않는다.

공개 조회에서 로그인 여부에 따라 결과를 달리하는 API는 비로그인을 허용하므로 principal이 null인 경우를 의도적으로 처리해야 한다. 비동기 작업·배치 등 HTTP 필터를 통과하지 않는 진입점도 별도 전제가 필요하다.

## 8. 질문 4: 조원들에게 전달할 안내 초안

> JWT 추출·검증·재발급·현재 로그인 세션 확인은 공통 인증 코드에서 담당합니다. 기능 컨트롤러는 `@AuthenticationPrincipal CustomUserDetails principal`로 검증된 사용자를 받아주세요.
>
> 현재 사용자 번호는 `principal.getUserId()`로 얻고, 서비스에는 필요한 값만 전달해주세요. 프론트가 보낸 작성자 ID를 현재 사용자로 신뢰하지 않습니다.
>
> 새 API를 만들 때 공개·로그인 필수·관리자 전용 여부를 인증 담당자와 공유해주세요. 로그인 필수 API가 Security 설정에서 보호된 것을 확인한 뒤 반복 null 검사를 줄입니다. 아직 보호되지 않은 API의 기존 검사는 먼저 삭제하지 말아주세요.
>
> 본인 글·본인 상담방인지 확인하는 소유권 검사는 기능 서비스에 유지해주세요. 사용자 객체 전체를 응답으로 반환하지 말고 화면에 필요한 응답 DTO를 사용해주세요.

## 9. 인증 담당자인 내가 해야 할 일

다음은 아직 완료했다고 볼 수 없는 후속 작업 목록이다.

- [ ] 조원별 API의 실제 경로·HTTP 메서드·공개 여부 목록 수집
- [ ] 공개·로그인 필수·관리자 전용 분류 합의
- [ ] SecurityConfig에 중앙 접근 정책 적용
- [ ] 토큰 검증 실패, 토큰 없음, 계정 비활성 상태의 응답 정책 확인
- [ ] 보호된 API의 반복 로그인 검사와 유지해야 할 소유권 검사 구분
- [ ] 사용자 주입 예시와 공통 401·403 응답 형식을 조원에게 공유
- [ ] 로그인 필수 API의 비로그인 401, 관리자 API의 일반회원 403 검증
- [ ] 본인 데이터 성공·타인 데이터 거절 검증
- [ ] 일반·자동 로그인 모두 Redis sid 검사와 로그아웃 후 차단 검증
- [ ] `/users/me`의 최소 응답과 마이페이지 상세 응답 분리 검토
- [ ] WebSocket 연결은 HTTP 컨트롤러와 별도로 로그인·방 접근 권한 검토

로그인 여부 안내를 위한 프론트의 `loggedIn`은 화면 편의용이다. API를 직접 호출할 수 있으므로 최종 허용·거절은 서버가 결정해야 한다.

## 10. 현재 확인한 것과 아직 결정하지 않은 것

현재 확인한 것:

- 일반 로그인도 sid와 Redis 기록을 사용한다.
- 토큰 검증은 서버 필터와 인증 서비스에서 실행한다.
- 현재 인증 객체는 UserDto를 감싸고, 기능 컨트롤러는 principal로 접근한다.
- 현재 접근 정책은 모든 회원 전용 기능을 중앙에서 보호하지 않으므로 컨트롤러의 null 검사가 남아 있다.

아직 결정·적용하지 않은 것:

- 전체 API별 공개·인증·관리자 정책
- 반복 null 검사를 제거할 구체적인 범위
- 인증 객체와 브라우저 응답의 최소 필드
- 응답 DTO 분리 및 프론트 사용처 변경

설명을 읽은 뒤 바로 기존 null 검사나 UserDto 필드를 지우는 작업부터 시작하지 않는다. **현재 동작을 확인하고, 공통 접근 정책과 응답 계약을 먼저 정한 뒤 기능별로 이전한다.**
