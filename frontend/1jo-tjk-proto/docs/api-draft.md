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
