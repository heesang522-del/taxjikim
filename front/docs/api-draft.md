# 프론트 API 연결 안내

## 마이페이지 저장 결과 목록 (TJK-231)

- `GET /api/v1/tax/inputs/saved-results`: 로그인 필수. 회원번호는 인증 정보에서 가져오며 요청 파라미터로 받지 않는다.
- `tax_inputs`와 `tax_results`를 연결해 본인의 `SAVED` 분석만 귀속연도 내림차순으로 반환한다. 결과 JSON 전체는 목록에 포함하지 않는다.
- 공통 응답의 `data`는 `[{ taxInputId, taxYear, savedAt, totalIncome, totalExpense, totalTax }]`이며, 저장 결과가 없으면 빈 배열이다. `savedAt`은 최근 JSON 저장 시각을 한국 시간 ISO 8601 문자열로 전달한다.
- 세 금액은 저장 JSON의 `breakdown.totalIncome`, `breakdown.totalExpense`, `breakdown.totalTax`에서 추출한 원 단위 숫자다. 총세금은 납부·환급 차액인 `settlementAmount`와 구분한다. 기존 목록 조회 한 번으로 비교 자료까지 반환하며 재계산하지 않는다.
- 결과 보기와 시뮬레이션은 각각 해당 화면에 `?taxInputId=분석번호&saved=true`로 이동해 기존 저장 결과 조회 API를 사용한다.

## 댓글 신고 백엔드 연동 (2026-11-06)

- `src/api/report.js`에 `createCommentReport(postId, commentId, reasonDetail)`을 추가했다: POST `/api/v1/posts/{postId}/comments/{commentId}/reports`.
- 백엔드 `com.kh.back.report.service.ReportServiceImpl`에 `createCommentReport`를 추가하고, `resolveReport`가 `targetType='COMMENT'`일 때도 처리(`DELETE`)되면 `CommentMapper.deleteByAdmin`으로 관리자 삭제(`COMMENTS.DELETED_BY_TYPE='ADMIN'`)하도록 분기했다. 본인 댓글 신고는 400 `CANNOT_REPORT_OWN_COMMENT`, 중복 신고는 409 `ALREADY_REPORTED`(게시글과 동일하게 DB 유니크 제약을 변환).
- `GET /api/v1/admin/reports` 응답에 `commentContent`·`commentDeletedAt`·`commentDeletedByType`을 추가했다(`REPORTS`를 `comments`에 LEFT JOIN). 게시글 신고 응답의 `postTitle` 등과 같은 방식이다.
- `CommentSection`은 이제 모든 댓글이 서버 댓글이라(목록을 항상 API로 불러옴) 바로 이 API를 호출한다. 게시글과 달리 "브라우저 전용 예시 댓글" 같은 mock 경로가 없어 분기가 필요 없었다.
- **댓글 신고의 mock(`useReports`의 `targetType==='comment'` 경로)은 이제 어디서도 쓰지 않는다.** `REPORT_SEED`를 빈 배열로 비웠고, `usePostStore`의 `comments`·`adminDeleteComment`(신고 댓글 mock 처리 전용으로 남겨뒀던 것)도 삭제했다. `useReports`/`REPORT_SEED` 자체는 남겨뒀다 — 브라우저에만 있는 예시 글(서버에 없는 postId) 신고가 여전히 이걸 쓴다.
- `AdminReportsPage`·`AdminPage`(`useAllReports`)는 이제 백엔드 신고(게시글+댓글)와 "브라우저 예시 글" mock만 합친다. mock 쪽은 `targetType==='post'`만 남았다.
- 진행 중 발견: 작업 중간에 `CommentApiController`·`SecurityConfig`·`src/api/comment.js`의 댓글 경로가 기존 `/api/posts/...`(버전 없음)에서 `/api/v1/posts/...`로 이미 바뀌어 있었다(이 작업에서 만든 변경이 아니다). 새로 추가한 `CommentReportApiController`와 `SecurityConfig`의 신고 경로 규칙은 바뀐 `/api/v1/posts/{postId}/comments/{commentId}/reports` 기준으로 맞췄다.

## 게시글 신고 백엔드 연동 (2026-11-06)

- `src/api/report.js`를 새로 추가했다. `createPostReport(postId, reasonDetail)`: POST `/api/v1/posts/{postId}/reports`. `getAdminReports()`: GET `/api/v1/admin/reports`(ADMIN 전용). `resolveReport(reportId, result)`: PATCH `/api/v1/admin/reports/{reportId}`, result는 `DELETE`\|`REJECT`.
- 백엔드는 `REPORTS`를 사용한다(`com.kh.back.report` 패키지). 사용하지 않는 별도 제재 이력 테이블은 DB 정리 대상이며 신고 기능에는 필요하지 않다. `TARGET_USER_ID`는 신고 시점의 글 작성자를 그대로 남긴다.
- 본인 글 신고는 400 `CANNOT_REPORT_OWN_POST`, 중복 신고는 DB의 `UNIQUE(REPORTER_ID, TARGET_TYPE, TARGET_ID)` 위반을 409 `ALREADY_REPORTED`로 변환해 응답한다.
- 처리(`DELETE`)하면 게시글을 관리자 삭제로 내리고(`POSTS.DELETED_BY_TYPE='ADMIN'`), 같은 글에 대기 중인 다른 신고도 함께 처리됨으로 바꾼다. 기존 자기 삭제(`deleteById`)는 `DELETED_BY_TYPE`을 기록하지 않아 그대로 두었다 — 자기 삭제한 글과 신고 전 삭제된 글은 관리자 화면에서 "삭제된 글"로만 구분되고 "관리자 삭제됨"과는 다르게 표시된다.
- `PostDetailPage`의 신고 버튼은 `post.source === 'server'`(실제 서버 글)일 때만 이 API를 호출한다. 브라우저에만 있는 예시 글은 여전히 mock 경로를 쓴다(실제 DB에 없는 postId라 API가 통하지 않기 때문).
- 알려진 제한: 관리자가 삭제한 **서버** 게시글을 다시 열면, `PostMapper.findById`가 `DELETED_AT IS NULL`로 걸러내 그냥 "게시글을 찾을 수 없습니다"(404)로 보인다. 로컬 mock 글에서 보이는 "관리자에 의해 삭제된 게시글입니다" 안내 화면은 서버 글에는 아직 연결되지 않았다 — 하려면 `findById`의 삭제글 필터링 방식을 바꿔야 해서(목록·다른 조회에도 영향) 이번 범위에는 넣지 않았다.

- 2026-11-03 월별 조회 통합: `findMonthlyTransactions`가 월·거래 구분별 합계를 한 번 조회한다. 수입은 세전 금액, 지출은 실제 금액을 합산한다. 서비스에서 기존 수입·지출 응답 목록으로 나누므로 화면 형식은 유지한다. 전체 DB 조회는 기본정보·기간·월별 합계 총 3회이며 아래 4회 조회 기록을 대체한다.
- 2026-11-03 정리: 기존 breakdown·월별 개별 API와 전용 서비스 메서드·TaxDto·프론트 월별 API 함수를 제거했다. 결과 조회는 calculation-result 하나만 사용한다. 아래 과거 개별 API 유지 기록은 더 이상 적용하지 않는다.
## 총세금·산출 근거 연결 (2026-11-02)

- `getTaxBreakdown(taxInputId, { signal })`: GET `/api/v1/tax/inputs/{taxInputId}/breakdown`.
- `TaxBreakdownDto`를 공통 응답 `data`로 반환한다. 총수입·총지출, 기부금 인정액, 사업소득, 기본공제, 과세표준, 세율·누진공제, 산출세액·표준세액공제·소득세·지방소득세·총세금을 포함한다.
- 결과 페이지가 한 번 조회해 `SummaryCards`와 `TaxChain`에 같은 결과·로딩·오류 상태를 전달한다. 요약 카드의 기존 개별 합계 요청은 제거했다.
- 월별 그래프는 기존 월별 API를 유지한다. 분석번호는 여전히 테스트용 `21`이며 입력 화면에서의 실제 분석번호 전달 연결은 별도 후속 작업이다.
- 오류는 서버 안내를 표시하며 예시 결과로 대체하지 않는다. 결과 저장은 기존 브라우저 저장 방식을 유지하되 화면에 표시한 서버 금액을 저장한다.

## 총수입·총지출 Spring 연결 (2026-10-29)

- `getTotalIncome(taxInputId, { signal })`: GET `/api/v1/tax/inputs/{taxInputId}/total-income`.
- `getTotalExpense(taxInputId, { signal })`: GET `/api/v1/tax/inputs/{taxInputId}/total-expense`.
- 응답 `data`는 `{ taxInputId, totalAmount }`이며 실제·예상 금액을 합산한 서버 계산 결과다.
- 결과 페이지에서 `SummaryCards`에 월별 그래프와 같은 분석번호를 전달한다. 카드에서 합계를 조회하며 실패 시 오류를 표시한다.

## 월별 그래프 Spring 연결 (2026-10-29)

- `getMonthlyIncomes(taxInputId, { signal })`: GET `/api/v1/tax/inputs/{taxInputId}/monthly-incomes`.
- `getMonthlyExpenses(taxInputId, { signal })`: GET `/api/v1/tax/inputs/{taxInputId}/monthly-expenses`.
- 응답은 공통 `ApiResponse`이며 `data`는 `[{ month, amount, predicted }]` 목록이다.
- 기존 공통 client와 Vite `/api` 프록시를 거쳐 Spring에 요청한다. 실패 시 그래프 영역에 오류를 표시하며 mock 데이터로 대체하지 않는다.
- 결과 페이지는 테스트 분석번호 `19`를 전달한다. 분석번호 전달 흐름 확정 후 이 값만 교체한다.
- 결과 페이지는 `<MonthlyChart taxInputId={taxInputId} />`로 분석번호만 전달한다. `MonthlyChart`에서 월별 API 호출·조회 상태 관리·그래프 표시를 함께 처리한다.

세금·ML 목표 요청·응답과 저장 시점은 [전체 명세](../../docs/creator-tax-dummy-format.md)를 따른다. 아래 건별 저장 등은 기존 연결 기록이다. 인증은 뒤의 쿠키 인증 통일 기록을 우선하며 오래된 Bearer·accessToken 인자를 현재 계약으로 간주하지 않는다.

## 알림 화면 연동 (2026-10-23)

- `src/api/notifications.js`를 mock(`mocks/notifications.json`)에서 실제 REST API로 교체했다. 백엔드는 이미 구현되어 있었고(`com.kh.back.notification`), 이번 작업에서 API 호출 함수와 헤더 알림 벨(`utils/useNotifications.js`)을 연결했다.
- `getNotifications()`: GET /api/v1/notifications — 로그인 회원의 알림 목록(최신순).
- `getUnreadNotificationCount()`: GET /api/v1/notifications/unread-count — 미읽음 수.
- `markAllNotificationsRead()`: PATCH /api/v1/notifications/read-all — 미읽음 전체 읽음 처리.
- 에러 처리는 `api/chat.js`의 `requestChat` 패턴을 그대로 따른다(success:false·HTTP 오류·네트워크 오류를 구분해 `Error`로 던진다).
- `isRead` 필드는 Lombok `isRead()` getter를 Jackson이 `read` 프로퍼티로 인식해 JSON 키가 `read`로 내려온다. Jackson+Lombok 조합으로 직접 재현해 확인했다(서버 기동 없이 별도 자바 스니펫으로 검증). 프론트는 `n.read`를 사용한다.
- `utils/useNotifications.js`: `usePostStore`(공지)·`useInquiryStore`(Q&A 답변 mock) 조합을 걷어내고 위 세 API로 교체했다. 실시간 알림 채널이 없어 로그인 중에는 15초 주기로 목록·안읽음 수를 함께 다시 불러온다. 벨을 열 때 `markAllRead()`를 호출하되 안읽음이 0이면 요청을 보내지 않는다.
- 알림 클릭 이동 경로: `type`(NOTICE/COMMENT)과 무관하게 `targetType`은 항상 `POST`이므로 `ROUTES.postDetail(targetId)`로 통일했다. `/support/qna/:id`(mock 문의 티켓)와는 무관한 별개 기능이라 경로 모호성이 없다.
- 범위 축소: Q&A "답변 완료" mock 알림(`useInquiryStore` 기반)은 포기했다. 백엔드의 `COMMENT` 알림이 QNA 게시글 댓글도 포함하지만 문구·대상이 다르므로 동일 기능은 아니다.
- `components/layout/NotificationModal.jsx`·`mocks/notifications.json`은 어디서도 참조하지 않던 죽은 코드라 삭제했다.

## 알림 화면 연동 (2026-10-23)

- `src/api/notifications.js`를 mock(`mocks/notifications.json`)에서 실제 REST API로 교체했다. 백엔드는 이미 구현되어 있었고(`com.kh.back.notification`), 이번 작업에서 API 호출 함수와 헤더 알림 벨(`utils/useNotifications.js`)을 연결했다.
- `getNotifications()`: GET /api/v1/notifications — 로그인 회원의 알림 목록(최신순).
- `getUnreadNotificationCount()`: GET /api/v1/notifications/unread-count — 미읽음 수.
- `markAllNotificationsRead()`: PATCH /api/v1/notifications/read-all — 미읽음 전체 읽음 처리.
- 에러 처리는 `api/chat.js`의 `requestChat` 패턴을 그대로 따른다(success:false·HTTP 오류·네트워크 오류를 구분해 `Error`로 던진다).
- `isRead` 필드는 Lombok `isRead()` getter를 Jackson이 `read` 프로퍼티로 인식해 JSON 키가 `read`로 내려온다. Jackson+Lombok 조합으로 직접 재현해 확인했다(서버 기동 없이 별도 자바 스니펫으로 검증). 프론트는 `n.read`를 사용한다.
- `utils/useNotifications.js`: `usePostStore`(공지)·`useInquiryStore`(Q&A 답변 mock) 조합을 걷어내고 위 세 API로 교체했다. 실시간 알림 채널이 없어 로그인 중에는 15초 주기로 목록·안읽음 수를 함께 다시 불러온다. 벨을 열 때 `markAllRead()`를 호출하되 안읽음이 0이면 요청을 보내지 않는다.
- 알림 클릭 이동 경로: `type`(NOTICE/COMMENT)과 무관하게 `targetType`은 항상 `POST`이므로 `ROUTES.postDetail(targetId)`로 통일했다. `/support/qna/:id`(mock 문의 티켓)와는 무관한 별개 기능이라 경로 모호성이 없다.
- 범위 축소: Q&A "답변 완료" mock 알림(`useInquiryStore` 기반)은 포기했다. 백엔드의 `COMMENT` 알림이 QNA 게시글 댓글도 포함하지만 문구·대상이 다르므로 동일 기능은 아니다.
- `components/layout/NotificationModal.jsx`·`mocks/notifications.json`은 어디서도 참조하지 않던 죽은 코드라 삭제했다.

## 1:1 채팅 상담 연동 (2026-10-20)

- 마이페이지 상담내역: `getMyChatHistory()`로 `/api/v1/chat/rooms/me/history`의 본인 종료 상담을 조회하고, 선택한 방은 기존 `getMemberMessages(roomId)`로 대화만 조회한다. 입력·전송·읽음 갱신 요청은 하지 않는다. 열린 상담은 `getMyRoom()`으로 조회해 상단에 안내만 표시한다.

- 회원 채팅 위젯(`ChatWidget`)과 관리자 상담 관리 화면(`AdminChatsPage`), 대시보드 대기 수를 `localStorage` mock(`useChatStore`)에서 실제 REST·WebSocket API로 교체했다.
- `src/api/chat.js`: 회원용(`getMyRoom`, `sendMemberMessage`, `getMemberMessages`, `markMemberRead`, `getMemberUnreadCount`)과 관리자용(`getAdminRooms`, `getAdminWaitingCount`, `enterAdminRoom`, `closeAdminRoom`, `sendAdminMessage`, `getAdminMessages`, `markAdminRead`) 함수. 전체 경로는 루트 `docs/api-draft.md`의 "채팅" 절을 따른다.
- `GET /api/v1/chat/rooms/me`는 기존에 없던 API로, 이번 작업에서 백엔드에 추가했다(회원이 가진 열린 방을 새로 만들지 않고 조회).
- `src/utils/useChatSocket.js`: `/ws/chat/{roomId}`에 연결하는 공통 WebSocket 훅. 보낸 메시지는 서버가 본인에게 되돌려주지 않아 화면에서 직접 붙인다(낙관적 갱신).
- 실시간 연결은 실제로 그 화면을 보고 있는 동안만 연다(회원: 패널 열림, 관리자: 담당 중인 방을 열어본 동안). 닫혀 있을 때는 REST로 안읽은 수만 주기적으로 확인한다.
- `vite.config.js`에 `/ws` 프록시(`ws: true`)를 추가해 개발 서버에서 WebSocket이 백엔드로 전달되게 했다.
- 범위 제한: 마이페이지의 "지난 상담" 목록과 헤더의 "관리자 답변" 알림은 이번 작업에서 제외했다(백엔드에 회원별 전체 상담방 목록 API가 없음). 마이페이지에는 준비 중 안내만 남겼다.

## 회원 채팅 종료 권한 추가 (2026-10-30)

- 기존에는 상담 종료가 관리자 전용이었다. `src/api/chat.js`에 `closeMemberRoom(roomId)`를 추가해 회원도 본인 상담방을 종료할 수 있게 했다(`POST /api/v1/chat/rooms/{roomId}/close`).
- 대기(WAITING)·진행중(IN_PROGRESS) 모두 종료 가능하다. 회원이 메시지를 보내면 바로 대기 방이 생기므로, 관리자가 입장하기 전에도 그 방을 취소할 수 있어야 하기 때문이다. `ChatWidget`의 "상담 종료" 버튼도 방이 있고 CLOSED가 아니면 항상 보인다(진행중일 때만 보이던 것에서 변경).
- `useChatStore.js`의 `useMemberChat`에 `close()`를 추가했다. 이미 종료된 상담(409 `ROOM_NOT_CLOSABLE`)은 에러로 표시하지 않고 조용히 넘어간다(중복 클릭·상대가 먼저 종료한 경우 대비).
- 백엔드(`ChatServiceImpl`)는 종료에 성공하면 종료한 쪽을 발신자로 "회원이 상담을 종료하였습니다." 또는 "관리자가 상담을 종료하였습니다." 메시지를 그 방에 저장한다. 누가 종료했는지 알 수 있도록 방 상태 문구가 아니라 채팅방 메시지로, 종료한 쪽에 따라 다른 문구로 남기기로 했기 때문이다.
- `close()`는 종료 후 `getMyRoom()`(열린 방만 찾는 API)을 다시 부르지 않는다. 그러면 방금 CLOSED된 방이 조회에서 사라져 "종료되었습니다" 안내가 안 보이는 버그가 있었다. 대신 메시지 목록만 다시 불러와(위 종료 메시지 수신) 방 상태를 로컬에서 바로 CLOSED로 바꾼다.
- 관리자가 대화 중인 방을 회원이 먼저 종료하면, 관리자 쪽은 다음 전송 시도에서 서버가 `CHAT_ROOM_CLOSED`로 거절하는데, 이 경우를 감지해 방 상태와 메시지 목록을 다시 불러오고 안내 문구("회원이 상담을 종료했습니다.")를 띄우도록 `useRoomMessages`에 `onRoomClosed` 콜백을 추가했다(`useAdminChat`·`useMemberChat` 양쪽에 연결). 이건 전송을 시도했을 때만 걸리는 뒤늦은 감지라, 아래 실시간 브로드캐스트를 추가한 뒤에도 네트워크 재연결 중 등 안전망으로 남겨뒀다.
- **실시간 브로드캐스트**: 종료한 사람 본인은 REST 응답으로 바로 반영되지만, 상대방은 그 방을 보고만 있어도(메시지를 보내려 하지 않아도) 똑같이 바로 알아야 한다. 그래서 백엔드가 상담 종료 시 그 방에 연결된 상대방의 WebSocket으로 종료 안내 메시지를 즉시 보낸다 — `{"type":"MESSAGE","message":{...},"roomClosed":true}`. `useChatSocket`이 이 `roomClosed` 플래그를 `onMessage`의 두 번째 인자로 넘기고, `useRoomMessages`는 메시지를 먼저 붙인 뒤(`setMessages`) `onRoomClosed`를 호출해 방 상태를 CLOSED로 바꾼다 — 역시 "메시지 먼저, 상태 변경은 그 다음" 순서를 지킨다. SSE 대신 이미 방마다 열려 있는 채팅 WebSocket을 그대로 썼다(알림 벨용 SSE는 채팅과 무관한 별개 채널이라 여기엔 안 맞음).
- 순서 보장: 종료 메시지("회원이/관리자가 상담을 종료하였습니다.")는 방 상태를 CLOSED로 바꾸기 전에 화면에 먼저 반영한다(메시지 목록 reload → 그 다음 상태 변경). 네 군데(본인 종료 두 곳, 상대 종료 감지 두 곳) 모두 이 순서를 지키도록 맞췄다. 반대 순서였다면 입력창이 먼저 사라진 뒤에야 종료 메시지가 보여 어색했다.
- (한때 "관리자가 입장해야 종료 버튼이 보인다"는 문제를 5초 폴링으로 고쳤다가, 대기중에도 종료를 허용하는 쪽으로 바꾸면서 폴링을 제거했다 — 버튼이 항상 보이므로 더 필요하지 않다.)

## 관리자 제한 계정 시각 (2026-10-16)

- GET /admin/account-locks 응답에 updatedAt을 추가한다. users.UPDATED_AT을 한국 시간대가 포함된 ISO 8601 문자열로 전달한다.

## 로그인 실패 정책 (2026-10-16)

- POST /auth/sessions: 1~5회 안내, 6회 30초·7회 1분·8회 3분·9회 5분 차단, 10회 LOCKED. 성공·관리자 해제 시만 초기화한다.
- 실패 응답의 error.details는 failedAttempts, remainingAttempts, blockedUntil, serverTime, locked를 포함한다. 일시 차단은 429 / LOGIN_TEMPORARILY_BLOCKED다.
- 별도 차단 상태 조회 API는 사용하지 않는다. 로그인 버튼 클릭 시 POST /auth/sessions 응답으로 차단 여부와 남은 시간을 확인한다.
- GET /admin/account-locks: ADMIN 전용 실제 LOCKED/BANNED/DORMANT 목록. 같은 관리자 해제 API로 휴면 계정도 복구한다. 휴면 복구 시 즉시 재전환되지 않도록 LAST_LOGIN_AT을 갱신한다.
- DELETE /admin/account-locks/{loginId}: ADMIN 전용 제한 해제, 기존 로그인 종료, 실패 기록 초기화.
- POST /auth/account-unlocks: 이전 이메일 잠금 해제 기능 종료, 410 / EMAIL_UNLOCK_DISABLED.

홈 화면(`getHomeData()`, `api/home.js`)은 아직 `mocks/home.json`을 쓰는 mock이다. 초기에 같이 쓰던 프로필·세금계산·커뮤니티·고객센터·관리자 mock 함수는 실제 API로 교체되며 해당 파일을 삭제했다. 현재 호출은 기능 코드로 확인한다.

## 로컬 상태 (API 아님)
- 거래 내역: `store/useTransactionStore.js` (zustand). 세액은 이 내역과 `utils/tax.js`로 화면에서 계산한다.
- 로그인 여부: 실제 쿠키 인증 결과를 메모리 상태에 반영한다. 새로고침 시 /api/v1/users/me로 확인한다.


## 로그인·로그아웃 실제 API 연결 (2026-10-14)

- 새 LoginForm과 Header 화면을 유지하며 실제 인증 API에 연결한다.
- 로그인: POST /api/v1/auth/sessions, loginId·password·rememberMe를 전송한다. rememberMe는 자동 로그인 체크 여부이며 기본값은 false다. true이면 기존 백엔드 정책에 따라 리프레시 토큰을 HttpOnly 쿠키로 발급하고, false이면 기존 리프레시 쿠키를 삭제한다.
- 성공 응답의 accessToken과 서버 회원 정보는 현재 페이지 메모리에만 보관한다. 세션 스토리지 저장·복원을 제거했으며 새로고침 시 GET /users/me로 기존 JWT 쿠키 인증 상태를 확인해 화면을 복원한다.
- 로그인 화면의 실패 횟수·잠금 판단은 서버 응답을 사용한다. 브라우저 mock 잠금 시간 안내는 적용하지 않는다.
- 로그아웃: DELETE /api/v1/auth/sessions/current를 기존 JWT 쿠키로 호출하고, 성공 후 화면의 인증 상태를 비운다.
- 회원가입·계정 찾기·마이페이지 등 다른 화면은 이번 작업에서 실제 API로 연결하지 않는다.
- 이름·닉네임이 로그인 응답에 없으므로 현재 화면용 사용자 표시에는 loginId를 사용한다.
- 모델 입력·출력·계산 로직은 변경하지 않는다.

## 인증·회원 화면 실제 연결 (2026-10-15)

- 회원가입: SignupForm → userApi의 중복 확인·이메일 발송/확인·가입 요청. mock 계정 저장과 브라우저 인증번호 생성은 사용하지 않는다.
- 요청 필드: loginId, password, name, nickname, email. 프로필 선택 시 multipart의 profile 파일로 보낸다. 전화번호 입력은 제거했다.
- 아이디는 영문·숫자·밑줄 4~20자, 이메일은 최대 255자·한글 및 내부 공백 불허·이메일 형식 검사로 백엔드와 통일한다. 앞뒤 공백은 제거한다. 가입·중복 확인·이메일 발송/확인·마이페이지 이메일 변경에서 필드별 `VALIDATION_ERROR`를 처리한다.
- 인증번호 안내는 5분, 인증 완료 후 가입 안내는 10분이며, 최종 유효성은 서버가 판단한다. 이메일을 바꾸면 화면의 이전 인증 상태를 초기화한다.
- 계정 찾기: 이름·이메일·인증번호로 로그인 아이디를 조회하고 화면에서 마스킹한다.
- 비밀번호 재설정: 계정과 이메일 일치 및 메일 인증 후 서버에 새 비밀번호를 전송한다. 성공하면 기존 Redis 세션도 종료한다.
- 마이페이지 회원 정보: GET /users/me로 읽고 닉네임·인증한 이메일·비밀번호·프로필 이미지 변경을 연결했다. 저장 성공 후 서버 정보를 다시 조회해 스토어를 갱신한다.
- 탈퇴: 복구 불가 경고의 확인 체크와 현재 비밀번호 입력 후에만 회원 삭제 버튼으로 DELETE /users/me를 요청한다. 성공 후 브라우저 인증 상태를 비운다. FK에 연결된 데이터가 있으면 서버 오류 안내를 표시하며 다른 테이블을 임의로 삭제하지 않는다.
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

## 게시판 직군 태그 제거 (2026-10-16)

- 게시글 등록·수정 API는 jobCategory를 받거나 저장하지 않는다.
- 게시글 수정 화면의 jobCategory 상태·전송과 상세 화면의 직군 태그 표시를 제거했다.

## 쿠키 인증 통일 (2026-10-16)

- 게시글·댓글·PDF·청크 업로드는 Bearer 헤더 없이 기존 경로로 쿠키 인증을 사용한다.
- 화면은 Zustand의 loggedIn으로 안내하고 실제 권한은 서버가 검사한다. 로그인·회원정보는 메모리에만 두고 새로고침 시 /api/v1/users/me로 복원한다.

## 인증 웹 스토리지 제거 (2026-10-16)

- 인증용 mock 계정·잠금·접속 기록도 메모리에서만 관리한다. 이전 인증 관련 웹 스토리지 키 6개는 앱 시작 시 양쪽 저장소에서 제거한다.
- 다른 기능의 게시글·상담·계산 기록 웹 스토리지 사용은 유지한다.
# 결과 데이터 통합 조회 (2026-11-03)

- 정산 시안 응답: `settlementType: 'PAYMENT' | 'REFUND' | 'NONE' | null`, `settlementAmount: number | null`. 총세금에서 실제 차감액 전액을 뺀 부호로 구분하고 금액은 절댓값이다. 입금액 누락 또는 차감액 음수이면 null이다. 해외 세금 공제한도·미래 원천징수액을 반영한 확정 납부/환급액이 아니다.

- 추가 응답 필드: `actualNetIncome: number | null`(실제 세후 수입 합계), `deductedAmount: number | null`(실제 세전 합계 − 실제 세후 합계). 수입 입금액 누락 시 둘 다 null이다. 추가 납부액이나 국내 기납부세액으로 확정한 값이 아니다. 최신 화면 합의에 따라 둘 다 카드에서 별도 표시하지 않으며, 추가 납부·환급 정산 연결은 후속 작업이다.

- `GET /api/v1/tax/inputs/{taxInputId}/calculation-result`
- 기본정보 조회 SQL에서 인증 회원번호와 `USER_ID`가 같거나 `USER_ID IS NULL`인 분석만 조회한다. 비회원 호출은 회원번호를 null로 전달하며 기존 비회원 계산을 유지한다. 조회되지 않으면 403 `TAX_INPUT_FORBIDDEN`을 반환하고 거래 조회·계산은 진행하지 않는다. 별도 소유자 조회는 추가하지 않는다.
- 공통 응답 `data`: `{ breakdown: TaxBreakdownDto, monthlyIncomes: MonthlyAmountDto[], monthlyExpenses: MonthlyAmountDto[], businessRegistered: boolean | null, actualIncome: number, predictedIncome: number, actualExpense: number, predictedExpense: number }`
- `actualIncome`·`actualExpense`는 실제 월별 금액 합계, `predictedIncome`·`predictedExpense`는 남은 달 예상 금액 합계다. 수입은 모두 세전 기준이다. 실제·예상 합계는 breakdown의 각 총액과 일치한다.
- 월별 항목: `{ month: number, amount: number, predicted: boolean }`. 실제·예상 금액을 서버에서 계산한다.
- 첫·마지막 거래 연도가 다르면 HTTP 400, `error.code: TAX_TRANSACTION_YEAR_MISMATCH`를 반환한다. 유효한 거래 날짜가 없으면 HTTP 400, `TAX_TRANSACTION_DATE_REQUIRED`를 반환한다.
- 페이지에서 한 번 조회하고 자식 컴포넌트에 전달한다. 기존 월별 API와 breakdown API는 유지하지만 결과 페이지에서는 개별 호출하지 않는다.
# 분석 기본정보 입력 계약 변경 (2026-11-03)

저장 `POST /api/v1/tax/save`의 `input`과 수정 `POST /api/v1/tax/inputs/{taxInputId}/update`의 `input`은 같은 필드를 사용한다.

- 비회원 분석은 토큰·세션 없이 분석번호로 저장·수정·삭제 및 기존 분석을 참조하는 PDF 추출을 요청한다. 분석번호를 아는 요청자는 비회원 분석에 접근할 수 있다.
- 회원 분석은 로그인한 소유자 또는 관리자에게 접근을 허용한다.
- 저장 응답을 받지 못하면 별도 저장 결과 조회 없이 화면에 저장 여부가 불명확하다고 표시한다.
- 서비스 입력 범위는 공제 대상 가족 수 0~100명, 기부금 및 실제 입출금액 0~9,999,999,999,999원(정수), 차감 세율 0~99.9999%(소수점 이하 4자리)다. UI는 가족 수·기부금의 빈 값을 0으로 전송하고, 직접 입력하는 금액 및 수입의 차감 세율은 필수다. 추출 데이터의 세율 미상은 null을 허용한다.
- UI는 앞뒤 공백을 제거하고 원화 금액의 천 단위 쉼표를 허용한다. 내부 공백·문자·음수·지수 표기는 거부한다. API에는 해당 값을 일반 JSON 숫자로 전달한다.
- 직접 입력 거래는 내역 확인·수정에서 저장한 뒤 분석 결과로 이동한다.

세전 수입은 실제 입금액과 차감 세율로 역산하며 최대 9,007,199,254,740,991원까지 허용한다. 조 단위 입금액을 저장하기 전에 기존 DB에서 세전 수입 컬럼을 확장한다.

```sql
ALTER TABLE TAX_TRANSACTIONS
  MODIFY COLUMN PRE_TAX_INCOME DECIMAL(18,2) NULL;
```

```json
{
  "input": {
    "taxYear": 2026,
    "businessRegistered": false,
    "dependentCount": 0,
    "generalDonationAmount": 0,
    "specialDonationAmount": 0,
    "saveStatus": "TEMP"
  }
}
```

- 금액 0은 기부금이 없는 입력 예시다. 두 기부금은 각각 음수가 아닌 DECIMAL(15,2) 저장 범위이며, 미입력은 저장 정규화에서 0으로 처리한다.
- 나이·감면·지역·기존 단일 기부금 필드는 저장 계약에서 제거한다. 요청의 분석번호·소유자는 기존 서버 결정 규칙을 유지한다.
- 기본정보 조회 응답도 generalDonationAmount·specialDonationAmount를 반환한다.
# 결과 JSON 보관 (TJK-202)

- `PUT /api/v1/tax/inputs/{taxInputId}/result`: 로그인 사용자의 결과 저장. 본문은 결과 페이지의 `result` 객체 전체다. 같은 분석번호는 덮어쓰며 성공 시 204를 반환한다.
- `GET /api/v1/tax/inputs/{taxInputId}/result`: 분석번호와 인증 회원번호를 JOIN 조회 조건으로 함께 사용해 본인의 저장 JSON만 `ApiResponse.data`로 반환한다. DB 조회는 한 번이며, 결과 미존재와 소유자 불일치를 구분하지 않고 403 `TAX_INPUT_FORBIDDEN`을 반환한다. 세금 재계산은 하지 않는다.
- 로그인 접근은 `SecurityConfig`에서 관리한다. 컨트롤러는 인증 정보의 회원번호를 전달하며 서비스에 로그인 검사나 세션 조회를 추가하지 않는다.
- 결과 저장 시 기존 `tax_inputs.USER_ID`에 인증 회원번호를 기록하고 `SAVE_STATUS`를 `SAVED`로 변경한다. 다른 회원에게 연결된 분석은 덮어쓰지 않는다. JSON 저장과 회원번호 기록은 같은 트랜잭션으로 처리한다.
- `tax_results` 컬럼은 추가하지 않는다. JSON은 기존 `RESULT_DETAIL`, 저장 시각은 `CALCULATED_AT`에 기록한다. 비회원 분석 소유권 증명 방식은 별도 작업이다.
- 결과 다시 보기 주소: `/calculator/result?taxInputId={분석번호}&saved=true`. 마이페이지 목록 UI 연결은 별도 작업이다.
- 기존 입력·거래 수정 시 결과를 삭제하는 무효화 동작은 유지한다. 저장 내용은 클라이언트가 보낸 당시 화면 값이며 서버 검증 계산 결과로 취급하지 않는다.
# 시뮬레이션 재계산

- `POST /api/v1/tax/inputs/simulation`
- 본문: `{ input, extraInc, extraExp, extraDonation }`. `input`에는 결과의 귀속연도·가족 수·총수입·총지출·특례기부금·일반기부금을 전달한다.
- 추가 수입은 세전 총수입에, 추가 지출은 총지출에, 추가 기부금은 일반기부금 입력액에 더한다.
- 기존 `TaxBreakdownDto` 계산 체인을 사용하며 기부금 인정 한도도 동일하게 적용한다.
- 응답: `ApiResponse.data`에 변경 후 `TaxBreakdownDto`. DB 조회·수정과 Python 호출은 하지 않는다.
- 기준 결과는 결과 페이지의 이동 state로 전달한다. 분석번호로 직접 접근하면 기존 결과 API에서 한 번 조회한다.
# 시나리오 DB 보관

- `GET /api/v1/tax/simulations?taxInputId={분석번호}`: 인증 회원과 분석번호가 모두 일치하는 저장 목록. 분석번호는 필수 요청 파라미터다. 응답은 `ApiResponse.data` 배열이다.
- `POST /api/v1/tax/simulations`: `{ taxInputId, extraInc, extraExp, extraDonation, totalTax, totalIncome, totalExpense }`를 기존 `SIMULATION`에 저장한다. 응답은 생성한 `simulationId`와 기준 `taxInputId`, 이름을 포함한 시나리오다.
- `DELETE /api/v1/tax/simulations/{simulationId}`: 본인 회원번호와 함께 일치하는 시나리오 삭제. 성공 시 204.
- 로그인 접근은 `SecurityConfig`에서 관리한다. 회원번호는 인증 정보에서 가져와 Mapper에 전달하며 요청 본문에서 받지 않는다.
- A/B/C 이름과 최대 세 개 정책은 회원의 분석번호별로 적용한다. 다른 분석에 저장된 시나리오는 한도에 포함하지 않는다. 한도 초과는 409 `SCENARIO_LIMIT_REACHED`로 반환한다.
- `INPUT_DATA_SNAPSHOT`에는 추가 수입·지출·기부금 JSON, 결과 금액 세 컬럼에는 저장 당시 계산값을 기록한다. `TAX_INPUT_ID`는 기준 분석을 가리키며 `ON DELETE CASCADE`로 연결한다. `DELETED_AT`·`RESULT_COMPARISON`은 제거한다.
- localStorage·sessionStorage·JSESSIONID 사용이나 데이터 이관은 추가하지 않는다.
- 시뮬레이션 계산 API `POST /api/v1/tax/inputs/simulation`도 로그인 필수이며 `SecurityConfig`에서 접근을 관리한다.


## AI 챗봇 복구 API (2026-11-06)

- 모든 `/api/v1/chatbot/**` 요청은 로그인 세션이 필요하다. 실제 백엔드 API를 사용한다.
- `GET /chatbot/conversations`: 본인 대화방 목록 조회.
- `POST /chatbot/conversations`: 새 대화 생성. 삭제되지 않은 대화방은 사용자별 최대 10개다.
- `PATCH /chatbot/conversations/{conversationId}/title`: 본인 대화의 이름을 변경한다. 요청은 `{ title: string }`이며, 앞뒤 공백을 제거한 1~40자를 받는다. 응답은 변경된 대화 정보다. 직접 지정한 이름은 첫 질문으로 덮어쓰지 않는다.
- `GET /chatbot/conversations/{conversationId}/messages`: 본인 메시지 조회.
- 대화 목록·메시지·사용량 조회는 회원당 10초에 합산 30건으로 제한하며 초과 시 `429 CHATBOT_READ_RATE_LIMIT`을 반환한다. 메시지 재조회에서 만료된 질문은 실패 처리하고 예약 횟수를 복구한다.
- `DELETE /chatbot/conversations/{conversationId}`: 대화방 논리 삭제.
- `GET /chatbot/usage`: 한국 날짜 기준 일일 질문 사용량 조회. 현재 한도는 30회다.
- `POST /chatbot/conversations/{conversationId}/messages`: 기존 일반 질문 API를 유지한다.
- `GET /chatbot/subscribe`: 세션 인증 SSE. `chatbot-ready` 수신 후 비동기 질문을 보낸다.
- `POST /chatbot/conversations/{conversationId}/messages/async`: `{requestId: UUID, message: string}`을 보내며 접수 시 202를 반환한다.
- SSE 이벤트는 `chatbot-delta`(delta), `chatbot-done`(최종 reply·remaining), `chatbot-failed`(code·message)이며 requestId와 conversationId로 요청을 구분한다.
- 답변 조각은 개인정보 검사 후 표시하고 완성된 답변을 DB에 저장한다. 실패한 질문도 사용 횟수에 포함된다.
- 화면 이탈·연결 오류는 자동 질문 재전송 없이 DB 기록 재조회로 확인한다. 서버 작업은 계속될 수 있다.
- OPENAI_API_KEY와 OPENAI_CHAT_MODEL은 실행 환경에서 지정한다. 실제 개인 거래·세금 분석 결과는 AI에 제공하지 않는다.
