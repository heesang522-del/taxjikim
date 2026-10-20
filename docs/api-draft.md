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
| POST | `/api/v1/auth/account-lookups/email-verifications` | JSON `name`, `email` |
| POST | `/api/v1/auth/account-lookups` | JSON `name`, `email`, `code`; data는 로그인 아이디 |
| POST | `/api/v1/auth/password-resets/email-verifications` | JSON `loginId`, `email` |
| POST | `/api/v1/auth/password-resets` | JSON `loginId`, `email`, `newPassword` |
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
현재 React 로그인은 `rememberMe: false`로 요청한다. 성공 응답의 토큰과 회원 번호·아이디·권한은 현재 페이지 메모리에만 보관한다. 세션 스토리지 저장·복원은 제거했으며 새로고침 시 GET /users/me로 기존 JWT 쿠키 인증 상태를 확인해 화면을 복원한다.
React 로그아웃은 Bearer 헤더를 전달하며, 서버 응답 성공 후 브라우저 인증 상태를 비운다. 명시적인 Bearer가 있으면 그 세션을 우선 처리하고, 현재 Redis sid와 일치하는 경우에만 종료한다.
회원 조회는 실제 테이블 `users`를 사용하고 삭제된 `DELETED_AT`을 조회하지 않는다. 탈퇴·복구 SQL의 후속 정리는 별도 작업이다.
계정 복구는 기존에 추가한 이메일·상태·탈퇴 기간 검사와 SQL 조건을 유지한다.
아이디 찾기·비밀번호 재설정은 2026-10-15에 기존 서비스를 호출하는 API와 화면을 연결했다. 비밀번호 재설정 성공 시 기존 Redis 로그인 세션을 종료한다. 잠금 해제 메일 링크 연결은 후속 작업이다.

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
회원가입은 이름·닉네임·로그인 아이디·비밀번호·이메일을 전송하며 생년월일은 선택이다. 프로필 이미지가 있으면 multipart 필드 `profile`로 전송한다. 삭제한 전화번호·직군·신고 알림 컬럼은 전송하지 않는다.
인증번호는 현재 서버 메모리 캐시에서 5분, 인증 완료 기록은 10분간 유지된다. 브라우저에서 인증번호를 생성하거나 응답에 노출하지 않는다.
이메일 변경은 새 이메일의 서버 인증 완료 기록이 필요하며 성공한 뒤 해당 기록을 소비한다.
탈퇴는 현재 비밀번호 확인 후 users 행을 즉시 삭제하고 로그인 세션과 인증 쿠키를 정리한다. 연결된 FK가 삭제를 막으면 409 오류를 반환한다. FK의 삭제 정책과 구 탈퇴 복구·스케줄러 정리는 후속 작업이다.
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

### 회원

| 메서드 | 경로 | 요청 |
| --- | --- | --- |
| GET | `/api/v1/chat/rooms/me` | 인증된 회원. 열린 방(WAITING/IN_PROGRESS)이 없으면 `data: null` |
| POST | `/api/v1/chat/messages` | JSON `content`. 열린 방이 없으면 새 대기 방을 만들고 그 방에 저장 |
| GET | `/api/v1/chat/rooms/{roomId}/messages` | 본인 방만 조회 가능 |
| PATCH | `/api/v1/chat/rooms/{roomId}/messages/read` | 상대(관리자)가 보낸 메시지를 읽음 처리 |
| GET | `/api/v1/chat/rooms/{roomId}/unread-count` | 상대가 보낸 안읽은 메시지 수 |

### 관리자 (`ADMIN` 권한, `/api/v1/admin/**`)

| 메서드 | 경로 | 요청 |
| --- | --- | --- |
| GET | `/api/v1/admin/chat/rooms` | 쿼리 `status` (WAITING/IN_PROGRESS/CLOSED) |
| GET | `/api/v1/admin/chat/rooms/{roomId}` | 방 단건 요약 조회(상태 무관). 새로고침 후 화면에서 선택했던 방 복원용 |
| GET | `/api/v1/admin/chat/rooms/waiting-count` | 대기 상담방 수 (대시보드) |
| POST | `/api/v1/admin/chat/rooms/{roomId}/enter` | 대기 → 진행중, 담당 관리자로 기록. 이미 처리 중이면 409 `ROOM_ALREADY_CLAIMED` |
| POST | `/api/v1/admin/chat/rooms/{roomId}/close` | 상담 종료. 담당 관리자 본인의 진행중 방만 가능 |
| POST | `/api/v1/admin/chat/rooms/{roomId}/messages` | JSON `content` |
| GET | `/api/v1/admin/chat/rooms/{roomId}/messages` | 담당 관리자만 조회 가능 |
| PATCH | `/api/v1/admin/chat/rooms/{roomId}/messages/read` | 회원이 보낸 메시지를 읽음 처리 |
| GET | `/api/v1/admin/chat/rooms/{roomId}/unread-count` | 방의 안읽은 메시지 수 |
| GET | `/api/v1/admin/chat/unread-count` | 담당 중인 모든 진행중 방의 안읽은 메시지 수 합계 |

방 응답(`ChatRoomDto`/`ChatRoomSummaryDto`)의 `status`는 `WAITING`/`IN_PROGRESS`/`CLOSED`이다. 메시지(`ChatMessageDto`)는 `messageId`, `roomId`, `senderId`, `content`, `isRead`, `createdAt`(ISO 8601)을 가진다.
`ChatRoomSummaryDto`의 `lastMessage`는 그 방의 가장 최근 메시지 내용이다(목록 미리보기용. 첫 메시지가 아니다).

### WebSocket

- 경로 `/ws/chat/{roomId}`. 연결 시 REST와 같은 JWT(쿠키 또는 `Authorization` 헤더)로 인증하며, 그 방의 회원 본인이거나 담당 관리자가 아니면 핸드셰이크에서 거절된다(401/403/404).
- 보낼 때: `{"content": "..."}` 문자열 메시지. 연결을 끊고 싶을 때는 문자열 `"quit"`을 보낸다.
- 받을 때: 저장 성공 시 `{"type":"MESSAGE","message": ChatMessageDto}`, 실패 시 `{"type":"ERROR","code":"...","message":"..."}`. 보낸 사람 본인에게는 되돌려주지 않으므로 화면에서 낙관적으로 붙여야 한다.
- 연결이 끊긴 동안 쌓인 메시지는 다시 보내주지 않는다. 재연결 후 REST 목록 API로 다시 불러오는 것은 클라이언트 책임이다. HTTP 응답 규격(`ApiResponse`)은 WebSocket 문자열 메시지에 적용하지 않는다.

## 검증 범위

로컬의 Git 제외 테스트로 API 경로·응답·오류·날짜 형식과 기존 계정 복구 조건을 검증한다.
실제 SMTP·S3 발송 및 게시글·파일 DB 동작은 이번 검증 범위에서 제외한다.
# 새 비밀번호 입력 조건 (2026-10-15)

- 회원가입의 password, 회원정보 변경·비밀번호 재설정의 newPassword는 8~20자, 공백 금지, 영문·숫자·특수문자(!@#$%^&*) 각각 하나 이상 포함 조건을 공통 적용한다.
- 위반 시 400 VALIDATION_ERROR와 해당 필드의 error.fields를 반환한다. 로그인·현재 비밀번호 대조에는 적용하지 않는다.
