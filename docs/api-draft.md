# 회원·인증 API 초안

## 데이터 이름

- `userId`: `USER_ID`에 대응하는 숫자 회원 고유 번호.
- `loginId`: `LOGIN_ID`에 대응하는 문자열 로그인 아이디.
- 회원 필드: `password`, `name`, `nickname`, `email`, `birthDate`, `profileImageUrl`, `userRole`, `userStatus`.
- `birthDate`는 선택값이며 `YYYY-MM-DD` 형식이다. 전화번호와 직군은 받지 않는다.
- 비밀번호는 요청에서만 받고, DTO 응답에는 해시를 포함하지 않는다.

## 회원 중복 검사

```text
GET /api/v1/users/check-id?loginId=test-creator
GET /api/v1/users/check-nickname?nickname=테스트닉네임
GET /api/v1/users/check-email?email=creator@example.com
```

중복이면 `data`는 `true`, 사용 가능하면 `false`이다.

```json
{ "success": true, "data": false, "meta": null, "error": null }
```

## 로그인

기존 인증 경로 `POST /api/auth/login`을 유지한다.

```json
{ "loginId": "test-creator", "password": "입력한 비밀번호", "rememberMe": false }
```

현재 로그인 응답:

```json
{
  "accessToken": "발급한 토큰",
  "tokenType": "Bearer",
  "userId": 1,
  "loginId": "test-creator",
  "userRole": "USER"
}
```

## 이메일 인증

기존 이메일 경로를 유지하고 입력 필드를 `email`로 통일한다.

```text
POST /api/email/send-code
POST /api/email/verify-code
```

```json
{ "email": "creator@example.com" }
```

```json
{ "email": "creator@example.com", "code": "입력한 인증번호" }
```

## 기존 폼 처리

- 회원 폼 경로는 `/member`에서 `/user`로 변경한다.
- `POST /user/join`은 기존 폼 방식이며 `loginId`, `password`, `name`, `nickname`, `email`, 선택 `birthDate`, 선택 파일 `profile`을 받는다.
- 이메일 입력 확인은 기존 가입 전 이메일 인증 흐름을 사용한다.
- 로그아웃 경로는 `/user/logout`이다.
- 기존 JSP 컨트롤러를 유지한 상태이며 React 회원가입·프로필 화면 연결은 별도 작업이다.

## 미결정 사항

- 인증·이메일 API의 `/api/v1` 경로 및 공통 응답 형식 통합.
- 회원가입 JSON API 및 React 화면 연결.
- 정확한 아이디·비밀번호·닉네임 입력 조건.

## 개발 중 변경 영향

Redis 세션 키는 `LOGIN:user-id:` 접두사를 사용한다. 이전 회원 세션 키를 조회하지 않으므로 변경 후 다시 로그인해야 한다.
