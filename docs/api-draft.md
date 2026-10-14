# 백엔드 API 초안

## 공통 규격

- HTTP API는 `/api/v1` 아래에 둔다. 화면 경로와 API 경로를 구분한다.
- 성공: `{ "success": true, "data": ..., "meta": null, "error": null }`.
- 실패: `{ "success": false, "data": null, "meta": null, "error": { "code": "...", "message": "..." } }`.
- 입력 오류는 `400`, `VALIDATION_ERROR`와 요청 필드 이름을 키로 하는 `error.fields`를 사용한다.
- 인증 필요·실패는 `401`, 권한 부족은 `403`, 없는 주소는 `404`, 메서드 오류는 `405`, 충돌은 `409`, 지원하지 않는 본문 형식은 `415`이다.
- 생성 성공은 `201`, 조회·수정·삭제 성공은 `200`이다.
- 목록은 `data`에 배열, `meta`에 `page`, `size`, `total`, `totalPages`를 담는다. 페이지 번호는 1부터 시작한다.
- 날짜·시각은 `Asia/Seoul` 기준 ISO 8601 문자열에 `+09:00`을 포함한다. 생년월일은 `YYYY-MM-DD`이다.
- ID와 금액은 JSON 숫자이며 비밀번호·해시는 응답에 포함하지 않는다.

## 인증

| 메서드 | 경로 | 요청 |
| --- | --- | --- |
| POST | `/api/v1/auth/sessions` | JSON `loginId`, `password`, `rememberMe` |
| DELETE | `/api/v1/auth/sessions/current` | `Authorization: Bearer {accessToken}` 또는 인증 쿠키 |
| POST | `/api/v1/auth/tokens` | `refreshToken` 쿠키 |
| POST | `/api/v1/auth/dormancy-recoveries` | JSON `loginId`, `email`, `code` |
| POST | `/api/v1/auth/deletion-recoveries` | JSON `loginId`, `email`, `code` |
| POST | `/api/v1/auth/account-unlocks` | JSON `token` |
| POST | `/api/v1/email-verifications` | JSON `email` |
| PATCH | `/api/v1/email-verifications` | JSON `email`, `code` |

로그인 요청:

```json
{ "loginId": "test_user", "password": "입력한 비밀번호", "rememberMe": false }
```

로그인 응답:

```json
{
  "success": true,
  "data": {
    "accessToken": "발급된 토큰",
    "tokenType": "Bearer",
    "userId": 1,
    "loginId": "test_user",
    "userRole": "USER"
  },
  "meta": null,
  "error": null
}
```

자동 로그인 선택·토큰 만료 시간·쿠키 Secure 설정·Redis 세션 교체 규칙은 기존 설정과 로직을 유지한다.
현재 React 로그인은 `rememberMe: false`로 요청한다. 성공 응답의 토큰과 회원 번호·아이디·권한을 같은 탭의 sessionStorage에 저장한다.
React 로그아웃은 Bearer 헤더를 전달하며, 서버 응답 성공 후 브라우저 인증 상태를 비운다. 명시적인 Bearer가 있으면 그 세션을 우선 처리하고, 현재 Redis sid와 일치하는 경우에만 종료한다.
회원 조회는 실제 테이블 `users`를 사용하고 삭제된 `DELETED_AT`을 조회하지 않는다. 탈퇴·복구 SQL의 후속 정리는 별도 작업이다.
계정 복구는 기존에 추가한 이메일·상태·탈퇴 기간 검사와 SQL 조건을 유지한다.
아이디 찾기·비밀번호 재설정 서비스와 메일 링크의 화면 연결은 이번 컨벤션 정리에서 새로 구현하지 않는다.

## 회원

| 메서드 | 경로 | 요청 |
| --- | --- | --- |
| POST | `/api/v1/users` | JSON `loginId`, `password`, `name`, `nickname`, `email`, 선택 `birthDate` |
| GET | `/api/v1/users/check-id` | 쿼리 `loginId` |
| GET | `/api/v1/users/check-nickname` | 쿼리 `nickname` |
| GET | `/api/v1/users/check-email` | 쿼리 `email` |
| GET | `/api/v1/users/me` | 인증된 회원 |
| PATCH | `/api/v1/users/me/nickname` | JSON `nickname` |
| PATCH | `/api/v1/users/me/email` | JSON `email` |
| PUT | `/api/v1/users/me/password` | JSON `currentPassword`, `newPassword` |
| PUT | `/api/v1/users/me/profile-image` | 폼 `deleteProfile`, 선택 파일 `profile` |
| DELETE | `/api/v1/users/me` | JSON `currentPassword` |

회원 고유 번호는 `userId`, 로그인 아이디는 `loginId`이다. 가입 성공의 `data`는 생성된 회원 고유 번호이다.
중복 검사 `data`는 중복이면 `true`, 사용 가능하면 `false`이다.
기존 `/user` 폼·JSP 컨트롤러 처리를 JSON API로 옮겼다. 신규 React 화면은 만들지 않는다.

## 회사

- `GET /api/v1/companies?page=1&size=20`
- `GET /api/v1/companies/{code}`

조회·페이지 제한 규칙과 기존 SQL은 유지한다.

## 게시글·파일

| 메서드 | 경로 | 요청 |
| --- | --- | --- |
| GET | `/api/v1/posts` | 쿼리 `sort`, `keyword`, `page` |
| GET | `/api/v1/posts/{postId}` | 게시글 번호 |
| POST | `/api/v1/posts` | 기존 등록 폼 항목 |
| PATCH | `/api/v1/posts/{postId}` | 기존 수정 폼 항목 |
| DELETE | `/api/v1/posts/{postId}` | 게시글 번호 |
| POST | `/api/v1/posts/upload-chunks` | 기존 청크 업로드 폼 |
| GET | `/api/v1/files/upload-urls` | 쿼리 `fileName` |
| POST | `/api/v1/files/metadata` | JSON `originalFileName`, `storedFileName`, `fileSize` |
| GET | `/api/v1/files/download-urls` | 쿼리 `storedFileName` |

게시글 폼은 `title`, `place`, `content`, `transportCost`, `foodCost`, `otherCost`, `savedFileNamesJson` 또는 `savedFileNames`를 받는다.
수정은 추가로 `deleteImageIds`를 받는다. 기존 파일명 해석·저장 규칙과 기본 금액을 유지한다.
청크 응답 `data`는 `{ "completed": false, "savedFileName": null }` 형태다.
게시글 날짜 필드는 `createdAt`, `updatedAt`이며 표시용 `formattedCreateAt`을 보내지 않는다.
현재 저장소에 게시글·파일 Mapper SQL이 없으며, 사용자 요청에 따라 추가하거나 수정하지 않았다.

## 채팅

- WebSocket 경로 `/ws/chat/{roomId}`는 그대로 유지한다.
- 기존 방별 메시지 전달·`quit` 종료 규칙을 유지한다. HTTP 응답 규격을 WebSocket 문자열 메시지에 적용하지 않는다.

## 검증 범위

로컬의 Git 제외 테스트로 API 경로·응답·오류·날짜 형식과 기존 계정 복구 조건을 검증한다.
실제 SMTP·S3 발송 및 게시글·파일 DB 동작은 이번 검증 범위에서 제외한다.
