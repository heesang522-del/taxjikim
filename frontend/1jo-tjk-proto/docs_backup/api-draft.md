# API 초안

## getAdminStats()  (`src/api/admin.js`)
관리자 화면의 모델 성능 지표와 데이터 현황. 현재는 `src/mocks/adminStats.json`을 300~800ms 지연 후 반환한다.

응답 예시 (예시값)
~~~json
{
  "model": { "accuracy": 0.912, "recall": 0.874 },
  "data": { "transactionCount": 1284 }
}
~~~
