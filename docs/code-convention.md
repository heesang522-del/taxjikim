# 코드 컨벤션

Java·Spring 백엔드, React·JavaScript 프론트엔드, Python·ML의 코드 작성 기준이다. Git 작업 절차는 별도의 GitHub 형상관리 규칙을 따른다.

## 1. 백엔드 규칙

### 1.1. 패키지와 클래스 이름

기능별 패키지 안에 역할별 클래스를 배치한다. 여러 기능에서 함께 사용하는 응답·예외 처리는 `common`에 둔다.

```text
user/
├── controller/UserApiController.java
├── service/UserService.java
├── service/UserServiceImpl.java
├── mapper/UserMapper.java
└── dto/UserDto.java

common/
├── dto/ApiResponse.java
├── dto/PageMeta.java
└── exception/
    ├── ApiException.java
    └── GlobalExceptionHandler.java
```

| 대상 | 이름 규칙 | 예시 |
| --- | --- | --- |
| 패키지 | 소문자, 기능 이름 | `user`, `company` |
| 클래스·파일 | PascalCase, 역할을 접미사로 표시 | `UserMapper.java` |
| 메서드·변수 | camelCase | `getUser`, `userId` |
| 상수 | UPPER_SNAKE_CASE | `MAX_PAGE_SIZE` |
| DTO | 대상 + Dto | `UserDto` |

Mapper XML은 `resources/mappers/`에 두고 인터페이스와 이름을 맞춘다.

```text
user/mapper/UserMapper.java
resources/mappers/UserMapper.xml
```

### 1.2. 클래스 역할과 메서드

| 구분 | 역할 |
| --- | --- |
| Controller | 요청 수신, 입력 검증, Service 호출, 응답 구성 |
| Service | 업무 규칙, 처리 흐름, 트랜잭션 경계 관리 |
| Mapper | DB 조회·저장, XML의 SQL과 연결 |
| DTO | 요청·응답 또는 조회 결과 데이터 전달 |

- Controller에 SQL이나 업무 로직을 작성하지 않는다.
- Service는 인터페이스와 구현체를 분리하고, Controller는 인터페이스를 주입받는다.
- 의존성은 `final` 필드와 생성자 주입을 사용한다. Lombok 사용 시 `@RequiredArgsConstructor`를 사용한다.
- 메서드는 하나의 역할을 중심으로 작성하고, 이름에 동작과 대상을 표시한다.

```java
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserApiController {
    private final UserService userService;

    @GetMapping("/{userId}")
    public ApiResponse<UserDto> detail(@PathVariable Long userId) {
        return ApiResponse.ok(userService.getUser(userId));
    }
}
```

메서드 이름 예시:

```text
Service: getUser, getUsers, createUser, updateUser, deleteUser
Mapper:  findById, findAll, insertUser, updateUser, deleteUser
```

### 1.3. 데이터와 API 일관성

- 같은 의미의 데이터는 API와 DTO에서 이름·타입·의미를 통일한다.
- JSON 필드 이름은 camelCase를 사용한다.
- 금액은 JSON 숫자로 전달하며, 쉼표나 통화 기호가 포함된 문자열로 반환하지 않는다. 예: `"price": 15000`. 원 단위 정수 금액은 JavaScript의 안전한 정수 범위 안에서 사용한다.
- 작성·수정 시각 등 시점 데이터는 시간대가 포함된 ISO 8601 문자열로 전달한다. 날짜만 잘라서 전달하거나 `3시간 전` 같은 화면용 문구로 반환하지 않는다. 예: `"createdAt": "2026-10-12T14:30:00+09:00"`.
- 작성·수정 시각은 한국 시간(`Asia/Seoul`, UTC+09:00) 기준으로 전달한다. 생일처럼 날짜만 의미 있는 값은 시간·시간대 없이 `YYYY-MM-DD` 문자열로 전달한다. 예: `"birthDate": "2000-05-21"`.
- DB의 자동 증가 ID는 `BIGINT`, Java DTO에서는 `Long`을 사용하고, JSON 응답에서는 숫자로 전달한다.
- 숫자 ID는 JavaScript의 안전한 정수 범위(`Number.MAX_SAFE_INTEGER`, 9,007,199,254,740,991) 안에서 사용한다. 별도의 큰 숫자 ID 생성 방식을 도입하면 전달 타입을 다시 검토한다.
- API 경로는 `/api/v1` 아래에 자원 이름으로 작성하고, 동작은 HTTP 메서드로 구분한다.
- 요청·응답 필드는 프론트와 합의하고, 변경 시 관련 호출 코드도 함께 확인한다.
- 응답에 비밀번호 등 비밀정보를 포함하지 않는다.

```text
사용자 식별자: userId — id, userNo 등과 혼용하지 않는다.
JSON 예시: { "postId": 15, "userId": 3 }

GET    /api/v1/users          목록 조회
GET    /api/v1/users/{userId} 단건 조회
POST   /api/v1/users          등록
PUT    /api/v1/users/{userId} 전체 수정
PATCH  /api/v1/users/{userId} 부분 수정
DELETE /api/v1/users/{userId} 삭제
```

MyBatis SQL의 값 바인딩에는 `#{...}`를 사용한다. `${...}` 문자열 치환에 사용자 입력을 직접 전달하지 않는다.

```xml
<select id="findById" resultType="UserDto">
    SELECT user_id AS userId, user_name AS userName
    FROM users
    WHERE user_id = #{userId}
</select>
```

### 1.4. 응답과 오류 처리

응답 본문이 있는 API는 `ApiResponse` 형식을 사용한다. 목록의 페이징 정보는 `meta`에 담고, 페이지 번호는 1부터 시작한다.

성공 응답 예시:

```json
{
  "success": true,
  "data": { "userId": 1, "userName": "홍길동" },
  "meta": null,
  "error": null
}
```

실패 응답 예시 — HTTP 상태 `404`:

```json
{
  "success": false,
  "data": null,
  "meta": null,
  "error": {
    "code": "USER_NOT_FOUND",
    "message": "사용자를 찾을 수 없습니다."
  }
}
```

| 항목 | 기준 |
| --- | --- |
| HTTP 상태 | 실패 원인에 맞는 상태 사용. 실패를 `200`으로 반환하지 않음 |
| `error.code` | 코드에서 구분할 고정 이름, UPPER_SNAKE_CASE |
| `error.message` | 사용자에게 전달할 설명 |

대표 상태: 잘못된 입력 `400`, 인증 필요 `401`, 권한 부족 `403`, 없는 자원 `404`, 중복·충돌 `409`, 서버 오류 `500`.

- 입력 검증 실패는 HTTP `400`, 오류 코드는 `VALIDATION_ERROR`로 반환한다. `error.fields`에 요청 필드 이름을 키로, 해당 필드의 오류 메시지를 값으로 담는다. 한 필드에 여러 오류가 있으면 대표 메시지 하나를 반환한다.
- 필드 이름은 요청 JSON의 이름과 맞춘다. 필드별 오류가 없는 일반 오류는 `fields`를 생략하거나 `null`로 반환할 수 있다.

입력 검증 실패 응답 예시:

```json
{
  "success": false,
  "data": null,
  "meta": null,
  "error": {
    "code": "VALIDATION_ERROR",
    "message": "입력값을 확인해주세요.",
    "fields": {
      "email": "이메일 형식이 올바르지 않습니다.",
      "password": "비밀번호는 8자 이상이어야 합니다."
    }
  }
}
```

예시의 비밀번호 길이는 설명용이며, 실제 입력 조건은 기능 요구사항에 따라 정한다. 공통 오류 응답 클래스와 예외 처리기는 `fields`를 지원하도록 구성한다.

- 예상 가능한 업무 오류는 Service에서 `ApiException`으로 전달한다.
- Controller마다 동일한 오류 응답용 try-catch를 반복하지 않고, `GlobalExceptionHandler`에서 처리한다.
- 잘못된 JSON·입력 검증 실패·지원하지 않는 메서드도 원인에 맞는 상태로 처리한다. 모든 예외를 `500`으로 바꾸지 않는다.
- 예상하지 못한 오류는 서버 로그에 남기고, 응답에는 내부 오류 내용과 비밀정보를 노출하지 않는다.

```java
throw new ApiException(
        HttpStatus.NOT_FOUND,
        "USER_NOT_FOUND",
        "사용자를 찾을 수 없습니다."
);
```

### 1.5. 코드 포맷

Java 코드는 팀에서 공유하는 IntelliJ 코드 스타일 설정을 따른다. 설정은 제공된 Java 코드 스타일 파일을 기준으로 구성하며, 코드 변경 후 포맷과 import 정리를 적용한다. 스타일 파일 적용 방법은 개발 환경 가이드에서 관리한다.

## 2. 프론트엔드 규칙

### 2.1. 폴더와 파일 이름

역할별 폴더 구조를 사용한다.

```text
src/
├── pages/UserListPage.jsx
├── components/UserCard.jsx
├── api/
│   ├── client.js
│   └── userApi.js
├── stores/useAuthStore.js
└── styles/
    ├── theme.js
    └── GlobalStyle.js
```

| 대상 | 역할·이름 규칙 |
| --- | --- |
| `pages/` | 라우트에 연결되는 화면, `UserListPage.jsx` |
| `components/` | 분리된 UI 조각, `UserCard.jsx` |
| `api/` | 공통 HTTP 클라이언트와 기능별 호출 함수, `userApi.js` |
| `stores/` | 공통 상태, `useAuthStore.js` |
| `styles/` | 테마와 전역 스타일 |

- 컴포넌트와 페이지 이름은 PascalCase, 확장자는 `.jsx`로 작성한다.
- 일반 JavaScript 파일은 camelCase, 확장자는 `.js`로 작성한다. `GlobalStyle.js`는 자료의 명칭을 유지한다.
- 변수·함수는 camelCase, 상수는 UPPER_SNAKE_CASE를 사용한다.
- API 파일의 기능 이름은 백엔드 패키지와 맞춘다. 예: `company` → `companyApi.js`.
- 프로젝트에서 작성하는 페이지·컴포넌트·API 함수·Hook·스토어·공통 client·스타일은 named export를 사용한다. 가져올 때는 중괄호로 내보낸 이름을 지정하며, 불필요한 별칭을 사용하지 않는다. 외부 라이브러리와 도구 설정 파일은 해당 라이브러리·도구가 요구하는 방식을 따른다.

```javascript
export function UserCard() { /* ... */ }
export async function getUsers() { /* ... */ }

// 다른 파일에서 가져오기
import { UserCard } from './components/UserCard';
import { getUsers } from './api/userApi';
```

### 2.2. 컴포넌트와 이벤트

- 함수형 컴포넌트를 사용한다.
- 페이지는 화면 구성과 데이터 조회 흐름을 담당한다. 반복되는 UI와 독립된 역할의 UI는 컴포넌트로 분리한다.
- 자식 컴포넌트는 전달받은 props를 직접 변경하지 않는다.
- 이벤트 처리 함수는 `handle`로 시작하고, 자식에 전달하는 이벤트 props는 `on`으로 시작한다.
- 목록의 `key`는 데이터의 고유 ID를 사용한다.

```jsx
// components/UserCard.jsx
export function UserCard({ user, onSelect }) {
  return (
    <li>
      <button type="button" onClick={() => onSelect(user.userId)}>
        {user.userName}
      </button>
    </li>
  );
}
```

```jsx
// 페이지 내부 사용 예시
function handleSelectUser(userId) {
  setSelectedUserId(userId);
}

// users.map 내부
<UserCard key={user.userId} user={user} onSelect={handleSelectUser} />
```

### 2.3. API 호출과 화면 상태

- 컴포넌트에서 Axios를 직접 호출하지 않는다. `api/`의 함수와 공통 `client`를 사용한다.
- API 함수는 공통 응답 본문을 반환하여 `data`와 `meta`를 모두 사용할 수 있게 한다.
- API 경로는 공통 client의 `/api/v1` 기준으로 작성한다.
- 호출 실패는 무시하지 않고 화면 또는 공통 오류 처리에서 다룬다.
- 입력 검증 실패(`VALIDATION_ERROR`)는 `error.fields`의 키를 입력 필드와 연결하여 해당 입력칸에 메시지를 표시한다. 필드별 메시지가 없는 오류는 `error.message` 또는 기본 안내 문구를 사용한다.
- 데이터 조회 화면은 로딩·실패·빈 결과·정상 결과를 구분한다.

```javascript
// api/userApi.js
import { client } from './client';

export async function getUsers(page = 1) {
  const response = await client.get('/users', {
    params: { page },
  });
  return response.data;
}
```

```javascript
// 페이지 내부 조회 예시
try {
  const result = await getUsers(page);
  setUsers(result.data);
  setPageMeta(result.meta);
} catch (error) {
  setErrorMessage(
    error.response?.data?.error?.message ?? '사용자 목록을 불러오지 못했습니다.',
  );
}
```

다음은 조회 결과를 표시하는 부분만 보여주는 예시이다.

```jsx
if (isLoading) return <p>불러오는 중입니다.</p>;
if (errorMessage) return <p role="alert">{errorMessage}</p>;
if (users.length === 0) return <p>등록된 사용자가 없습니다.</p>;
```

### 2.4. 상태와 Hook

- API에서 받은 자동 증가 숫자 ID는 `number`로 사용하고, 같은 ID를 문자열과 숫자로 혼용하지 않는다. URL 경로·쿼리에서 읽은 ID는 문자열이므로 API 데이터와 비교할 때 타입을 맞춘다.
- API에서 받은 작성·수정 시각은 날짜·시간 원본을 유지하고, 화면에 맞게 표시 형식을 변환한다. 예: `2026-10-12T14:30:00+09:00` → 목록에서는 `2026.10.12`, 상세에서는 `2026.10.12 14:30`.
- API에서 받은 금액은 `number`로 사용한다. 쉼표·통화 기호는 화면에 표시할 때 적용한다. 예: `15000` → `15,000원`.
- 작성·수정 시각은 한국 시간(`Asia/Seoul`) 기준으로 표시한다. 생일처럼 날짜만 전달된 값에는 시간대 변환을 적용하지 않는다.
- 한 화면에서만 쓰는 값은 해당 컴포넌트의 state에 둔다.
- 여러 화면에서 공유하는 로그인 정보 등은 공통 스토어에 둔다. 모든 상태를 스토어로 옮기지 않는다.
- 검색어·필터·페이지처럼 새로고침이나 공유 후 유지할 값은 URL 쿼리로 관리한다.
- `useState`, `useEffect` 등 일반 Hook은 컴포넌트 또는 커스텀 Hook의 최상위에서 호출한다.
- state는 직접 수정하지 않고 setter로 갱신한다. 이전 값에 의존하면 함수형 갱신을 사용한다.
- Effect의 의존성을 임의로 생략하지 않는다. API 조회 Effect는 이전 요청이 늦게 도착해 최신 화면을 덮지 않도록 정리 처리를 한다.

```javascript
const [isModalOpen, setIsModalOpen] = useState(false);

setUsers((prevUsers) => [...prevUsers, newUser]);
```

### 2.5. 스타일과 화면 주소

- 공통 색상·간격은 `theme`를 사용하고, 전역 초기화는 `GlobalStyle`에 둔다.
- 화면별 스타일은 해당 컴포넌트의 styled-components에 작성한다.
- 스타일 전용 props는 `$` 접두사를 사용한다.
- 공통 메뉴·헤더는 `Layout`에 두고, 자식 화면은 `Outlet`으로 표시한다.
- 화면 경로는 소문자로 작성하고, 여러 단어는 하이픈으로 구분한다.

```jsx
const Status = styled.span`
  color: ${({ $active, theme }) =>
    $active ? theme.colors.primary : theme.colors.text};
`;

<Status $active={isActive}>상태</Status>
```

```text
화면: /users, /users/1, /users/new, /users/1/edit
API:  /api/v1/users, /api/v1/users/1
검색: /users?q=홍길동&page=2
```

### 2.6. 코드 포맷

React 코드는 프로젝트에 공유된 Prettier 설정을 따른다. 설정은 제공된 React 기본 세팅 자료를 기준으로 구성하며, 개인 취향에 따라 들여쓰기·따옴표·줄바꿈을 다르게 적용하지 않는다. 코드 변경 후 포맷을 적용한다.

## 3. Python·ML 규칙

1. **이름**: 파일·함수·변수는 `snake_case`, 클래스는 `PascalCase`, 상수는 `UPPER_SNAKE_CASE`를 사용한다.
2. **포맷**: 들여쓰기는 공백 4칸을 사용한다. 포맷터를 도입하면 팀에서 하나로 통일한다.
3. **파일 배치**: API는 `ml/app/`, 데이터 준비는 `ml/scripts/`, 실험은 `ml/notebooks/`에 둔다. 필요한 시점에 파일을 추가하며, 단순한 코드를 불필요하게 분리하지 않는다.
4. **주석·타입**: 복잡한 계산과 데이터 처리 의도는 한국어 주석으로 설명한다. API와 주요 함수에는 매개변수·반환값 타입 힌트를 작성한다.
5. **전처리·ML 처리**: 모델에 필요한 전처리는 가능한 경우 scikit-learn의 `Pipeline`으로 모델과 함께 관리한다. 전처리 기준은 학습 데이터로만 학습하고, 검증·예측에는 같은 기준을 적용한다. 피처 계산도 학습과 예측에서 공유한다. 거래 분류는 텍스트 전처리·TF-IDF·분류 모델을 일관되게 적용하고, 같은 데이터 분할로 후보 모델을 비교한다. 시계열 예측은 미래 정보가 학습에 섞이지 않도록 시간 순서로 데이터를 분리한다.
6. **설정·보안**: 개인 PC의 절대 경로를 코드에 고정하지 않는다. 비밀번호·키는 환경변수 등으로 관리하고 코드에 직접 작성하지 않는다.
7. **패키지**: 라이브러리를 추가·변경하면 가상환경에서 `requirements.txt`를 갱신하고 변경 내용을 확인한다.
