# 택스지킴 (TaxJiKim)

KH Academy 파이널 팀 프로젝트로 개발 중인 금융 데이터 분석·ML 서비스입니다.
크리에이터의 수입·지출 거래를 정리하고, 거래 분류와 예상세액 계산, 조건별 시뮬레이션을 지원합니다.

> 현재 개발 중입니다. 아래 구현 현황은 저장소 코드 기준이며, 전체 서비스의 실행 검증 완료를 의미하지 않습니다. 계산 결과는 예상값이며 실제 신고 세액과 다를 수 있습니다.

## 주요 기능과 구현 현황

| 기능 | 내용 | 현재 상태 |
| --- | --- | --- |
| 회원·인증 | 회원가입, 로그인, 이메일 인증, 계정 찾기, 프로필 관리 | 프론트·백엔드 API 코드 구현. 이메일 발송 설정 필요 |
| 거래 입력·분류 | 직접 입력, CSV 가져오기, PDF 거래 추출·OCR, 수입·지출 카테고리 분류 | Spring–FastAPI 연결 및 TF-IDF + LinearSVC 추론 코드 구현 |
| 거래 검토·저장 | 추출 내역 검토, 카테고리 수정, 거래 삭제·저장 | 백엔드 API 연동 코드 구현 |
| 예상세액 분석 | 예상세액과 산출 근거, 월별·카테고리별 내역, 결과 저장·조회 | 백엔드 계산·저장 API 연동 코드 구현 |
| 시뮬레이션 | 입력 조건 변경에 따른 계산 비교, 시나리오 저장·삭제 | 백엔드 API 연동 코드 구현 |
| 커뮤니티·고객지원 | 게시글·댓글, 공지·FAQ·문의, 신고와 관리자 처리 | API 연동 코드 구현. 일부 브라우저 예시 데이터 처리도 존재 |
| 상담 | AI 챗봇, 회원·관리자 실시간 채팅 | OpenAI 연동, SSE·WebSocket 코드 구현. 외부 서비스 설정 필요 |
| ML 재학습 | 사용자 수정 피드백 수집, 추가 학습 자료 검토, 자동 재학습·모델 적용 | 처리 코드 구현. 자료와 실행 환경에 따른 확인 필요 |
| 홈 화면 | 서비스 소개와 통계 | 홈 통계 데이터는 mock 사용 |

실제 API 호출과 mock 데이터가 함께 있습니다. 화면이 표시되는 것만으로 DB 저장이나 외부 서비스 연결이 확인된 것은 아닙니다.
예시 금융 수치는 [확인 목록](front/docs/to-verify.md)에서 관리합니다.

## 기술 스택

| 영역 | 기술 |
| --- | --- |
| 프론트엔드 | React 19, Vite 8, React Router, Axios, Zustand, styled-components, Recharts |
| 백엔드 | Java 17, Spring Boot 4, Spring Security, JWT, MyBatis, Spring AI |
| 데이터·실시간 통신 | MySQL, Redis, WebSocket, SSE |
| ML·문서 분석 | Python, FastAPI, scikit-learn, TF-IDF + LinearSVC, pdfplumber, PyMuPDF OCR |
| 외부 연동 | OpenAI API, SMTP 이메일, AWS S3 관련 의존성 |

## 폴더 구조

```text
front/                # React 화면, API 호출, mock 데이터
  src/                # 페이지·컴포넌트·상태·API 코드
  docs/               # 화면·API 기록, 예시 금융 수치 확인 목록
back/                 # Java·Spring Boot 백엔드
  src/main/           # 기능별 Java 코드, 설정, MyBatis Mapper
  local/schema.sql    # 로컬 개발용 DB 생성 SQL
ml/                   # Python·FastAPI 및 분류 모델
  app/                # PDF·OCR 분석, 추론 API
  scripts/            # 학습·시험·피드백 전처리·재학습
  models/             # 기본 모델 및 재학습 결과
  docs/               # ML 파일 안내와 재학습 문서
docs/                 # 공통 규칙, API·세금 계산·연결 명세
```

## 로컬 실행

명령은 Windows PowerShell 기준입니다. 프로젝트 루트에서 시작하고, 각 서버는 별도 터미널에서 실행합니다.

### 준비 환경

- Node.js: 잠금 파일의 패키지 요구사항 기준 `^22.18.0` 또는 `>=24.11.0`
- JDK 17: 저장소의 Gradle Wrapper 사용
- MySQL: 로컬 생성 SQL은 MySQL 8.0 기준
- Redis: 인증·세션 관련 기능에 사용
- Python 3.14: 기존 개발 환경 안내 기준. 의존성은 `ml/requirements.txt`에서 관리

### 1. DB와 백엔드 설정

MySQL·Redis를 준비하고, 새 로컬 DB라면 [schema.sql](back/local/schema.sql)을 확인한 뒤 MySQL 클라이언트에서 실행합니다.
이 SQL은 초기 데이터가 없는 설계 변환본으로, 실제 서버의 최신 구조와 일치하는지 추가 확인이 필요합니다. 기존 DB에 그대로 재실행하지 않습니다.

[application.yml](back/src/main/resources/application.yml)의 설정을 확인하고 실행 터미널에 필요한 환경변수를 지정합니다.
실제 비밀번호나 API 키를 저장소에 작성하지 않습니다.

| 환경변수 | 용도 |
| --- | --- |
| `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | 본인 개발 DB의 JDBC 주소·계정 |
| `REDIS_HOST`, `REDIS_PORT` | Redis 연결. 기본값 `localhost`, `6379` |
| `SERVER_PORT` | 백엔드 포트. 기본값 `8080` |
| `PYTHON_API_URL` | ML 서버 주소. 기본값 `http://127.0.0.1:8000` |
| `MAIL_USERNAME`, `MAIL_PASSWORD` | 이메일 발송 계정·앱 비밀번호 |
| `OPENAI_API_KEY`, `OPENAI_CHAT_MODEL` | AI 챗봇 API 키·모델 |

JWT 관련 설정은 `jwt` 항목을 확인합니다. 로컬 프로필을 사용한다면 `application-local.yml`의 설정도 확인합니다.
Spring Boot가 `.env` 파일을 자동으로 읽도록 구성된 상태는 아닙니다.

```powershell
cd back
.\gradlew.bat bootRun
```

IntelliJ에서는 `back/build.gradle`을 Gradle 프로젝트로 열고 Gradle JVM을 JDK 17로 지정한 뒤 `com.kh.back.BackApplication`을 실행할 수도 있습니다.

### 2. ML 서버

```powershell
cd ml
py -3.14 -m venv .venv
.\.venv\Scripts\python.exe -m pip install -r requirements.txt
.\.venv\Scripts\python.exe -m uvicorn app.main:app --reload --port 8000
```

- 가상환경 생성·설치는 최초 설정 또는 의존성 변경 시 수행합니다.
- 서버 시작 시 수입·지출 기본 모델을 모두 읽습니다. `models/income_classifier/linear_svc_bundle.pkl`, `models/expense_classifier/linear_svc_bundle.pkl`이 필요합니다.
- 모델이 없으면 학습 자료를 준비한 뒤 `ml/`에서 아래 명령으로 생성합니다. 자료·모델 구성은 [ML 파일 안내](ml/docs/file-guide.md)를 참고합니다.

```powershell
.\.venv\Scripts\python.exe scripts/train_income.py
.\.venv\Scripts\python.exe scripts/train_expense.py
```

- OCR에는 `ml/tessdata/kor.traineddata`, `ml/tessdata/eng.traineddata`가 필요합니다.
- 상태 확인: [http://127.0.0.1:8000/health](http://127.0.0.1:8000/health) → `{"status":"UP"}`
- API 문서: [http://127.0.0.1:8000/docs](http://127.0.0.1:8000/docs)
- 서버 시작 시 재학습 스케줄러도 실행됩니다. [재학습 안내](ml/docs/retraining.md)와 [스케줄러 안내](ml/docs/retrain-scheduler.md)를 확인합니다.

IDE의 FastAPI 전용 실행 구성 없이도 위 터미널 명령으로 실행할 수 있습니다.

### 3. 프론트엔드

```powershell
cd front
npm ci
npm run dev
```

- 접속: [http://localhost:5173](http://localhost:5173). 포트가 사용 중이면 터미널에 표시된 주소를 확인합니다.
- `npm ci`는 최초 설치 또는 의존성 변경 시 수행합니다.
- 개발 서버는 `/api`, `/uploads`, `/ws` 요청을 `http://localhost:8080`으로 전달합니다.
- API 기능을 확인하려면 백엔드가 필요하고, PDF 분석에는 ML 서버도 필요합니다.
- 각 서버 종료: 실행 중인 터미널에서 `Ctrl+C`

### 실행 후 확인

1. ML `/health` 응답과 Spring Boot 시작 로그를 확인합니다.
2. 프론트에서 회원가입·로그인과 `/calculator/input`의 입력 화면을 확인합니다.
3. 개인정보가 없는 테스트 파일로 PDF 분석·카테고리 수정·저장을 확인합니다.
4. 계산 결과와 시뮬레이션을 확인하고, 저장 결과를 다시 조회합니다.

이번 README 갱신에서는 코드·설정·문서를 대조했으며, 서버 실행과 브라우저 통합 검증은 수행하지 않았습니다.

## 팀원 및 역할

팀원 이름과 담당 범위를 확인할 수 있는 확정 자료가 없어 추후 작성합니다.

## 화면 및 시연

대표 화면 스크린샷과 시연·배포 링크는 확인 후 추가할 예정입니다.

## 협업·참고 문서

- [문서 안내](docs/README.md)
- [코드 컨벤션](docs/code-convention.md) · [Git 규칙](docs/git-rule.md) · [PR 가이드](docs/pr-guide.md)
- [백엔드 API](docs/api-draft.md) · [프론트 API 기록](front/docs/api-draft.md)
- [예상세액 계산 기준](docs/creator-tax-notes.md) · [거래·ML 전체 연결 명세](docs/creator-tax-dummy-format.md)
- [화면 기록](front/docs/model-card.md) · [예시 금융 수치 확인 목록](front/docs/to-verify.md)
- [ML 파일 안내](ml/docs/file-guide.md) · [재학습 안내](ml/docs/retraining.md)

비밀 설정, 실제 개인정보, 가상환경, 설치·빌드 결과는 공유하지 않습니다. 기본 서비스 모델과 로컬 재학습 산출물의 공유 기준은 ML 문서와 `.gitignore`를 확인합니다.
