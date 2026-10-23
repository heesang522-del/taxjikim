# taxjikim
khacademy final team project taxjikim - React / Spring Boot / ML

금융 데이터 분석·ML 서비스 개발 프로젝트입니다. 기능 소개와 최종 결과는 프로젝트 마무리 시 정리합니다.

## 폴더 구조

```text
front/  # React + Vite 화면과 API 호출
back/   # Java + Spring Boot 백엔드
ml/     # Python + FastAPI 작업 공간
docs/   # 공통 규칙과 API 문서
```

현재 작업 폴더의 ML API는 상태 확인을 제공하며 실험용 지출 학습 코드가 있습니다. PDF용 Spring·FastAPI 연결은 feature/TJK-5 작업과 대조하며 분류 모델 추론 연결은 아직입니다. 프론트에는 mock 화면과 실제 API 호출이 함께 있습니다.

## 개발 환경

| 영역 | 준비 항목 |
| --- | --- |
| React | Node.js와 npm. 잠금 파일의 패키지 요구사항은 Node.js `^22.18.0` 또는 `>=24.11.0` |
| Spring Boot | JDK 17, MySQL, Redis 및 담당자가 안내하는 로컬 설정 |
| ML | Python 3.14 (현재 로컬 확인 버전: 3.14.7) |

아래 명령은 Windows PowerShell 기준이며, 각 서버는 별도 터미널에서 실행합니다. Gradle은 저장소의 Wrapper를 사용하므로 별도 설치하지 않습니다.

## React 설치 및 실행

프로젝트 최상위에서 실행합니다.

```powershell
cd front
npm ci
npm run dev
```

- `npm ci`는 최초 설치 또는 의존성 변경 시 실행합니다.
- 브라우저: http://localhost:5173 (포트가 사용 중이면 터미널에 표시된 주소 확인)
- 개발 서버의 `/api`, `/uploads` 요청은 `http://localhost:8080`의 Spring Boot로 전달됩니다.
- API를 사용하는 기능을 확인하려면 백엔드도 실행해야 합니다.
- 종료: `Ctrl+C`

## Spring Boot 설정 및 실행

1. MySQL·Redis 실행과 프로젝트용 DB 스키마 준비를 담당자에게 확인합니다.
2. DB 접속정보, Redis, JWT 및 기능별 메일·S3 설정을 담당자에게 확인해 로컬 설정을 준비합니다. 현재 저장소에는 기존 README에서 안내하던 `application.example.yml`과 `application.example.properties`가 없습니다. 설정 예시와 DB 초기화 절차의 공유는 추가 정리가 필요합니다.
3. IntelliJ에서 `back/build.gradle`을 Gradle 프로젝트로 불러오고 Gradle JVM을 JDK 17로 지정합니다.
4. `com.kh.back.BackApplication`을 실행하거나, 별도 터미널에서 아래 명령을 실행합니다.

```powershell
cd back
.\gradlew.bat bootRun
```

- 의존성은 Gradle이 다운로드합니다.
- 프론트 프록시는 백엔드 8080 포트를 사용하므로 로컬 서버 포트도 맞춥니다.
- 실행 로그에서 서버 시작 완료를 확인한 후 프론트에서 필요한 API 기능을 확인합니다. 백엔드 루트 주소에 화면이 제공된다고 가정하지 않습니다.
- 실제 비밀번호·키를 README에 작성하지 않습니다. `.env`를 Spring Boot가 자동으로 읽도록 설정한 상태는 아닙니다.
- 현재 문서 정리에서는 React·Spring 서버를 새로 실행해 검증하지 않았습니다.

## GitHub에 올리는 파일과 로컬 파일

- 최상위 `.gitignore`는 같은 저장소의 `front`, `back`, `ml`에 함께 적용됩니다. 각 폴더의 기존 `.gitignore`도 유지합니다.
- 소스 코드, mock 예시 데이터, `package.json`, `package-lock.json`, Gradle Wrapper, 설정 예시 파일은 공유합니다.
- 비밀 설정, 설치 파일, 빌드 결과, IDE 개인 설정, 로컬 백업, Python 가상환경, 로컬 ML 데이터·학습 결과는 제외합니다.
- `git rm --cached`로 추적을 해제한 파일은 PC에 그대로 남습니다. Git에서 보이는 삭제 표시는 저장소에서 제외한다는 뜻입니다.

## ML 개발 환경 설정 (Windows PowerShell)

현재 로컬에서 확인한 Python 버전은 3.14.7입니다. `ml/requirements.txt`에는 설치한 라이브러리 버전을 기록합니다.
실험용 학습 코드는 있으며 현재 작업 폴더의 예측 API 연결은 아직입니다. [ML 작업 상태](ml/docs/home-handoff.md)를 확인합니다.

### 처음 한 번 설정

프로젝트 최상위 폴더에서 실행합니다. Python 3.14가 설치되어 있어야 합니다.

```powershell
cd ml
py -3.14 -m venv .venv
.\.venv\Scripts\python.exe -m pip install -r requirements.txt
```

`.venv`는 각자 PC에서 생성합니다. 가상환경을 활성화하지 않아도 위처럼 Python 경로를 지정해 설치·실행할 수 있습니다.

### IntelliJ IDEA에서 Python 환경 연결

<details>
<summary>선택 사항: Python SDK와 실행 버튼 설정</summary>

앞 단계에서 `ml/.venv` 생성과 라이브러리 설치를 마친 뒤 진행합니다. IDE 버전과 플러그인에 따라 메뉴 이름이 다를 수 있습니다.

1. `File > Settings > Plugins`에서 JetBrains의 Python 플러그인을 설치·활성화하고, 안내가 나오면 IDE를 재시작합니다.
2. `File > Project Structure` (`Ctrl+Alt+Shift+S`)를 엽니다.
3. `SDKs > + > Add Python SDK from disk...`에서 기존 환경의 Python 실행 파일을 선택합니다. 버전에 따라 `Python` 또는 `Existing environment`로 표시됩니다.
4. 실행 파일은 프로젝트 폴더 아래 `ml\.venv\Scripts\python.exe`로 지정합니다. 예: `C:\kh_final\taxjikim\ml\.venv\Scripts\python.exe`.
5. Java와 함께 연 프로젝트라면 `Modules`에서 ML용 Python 모듈의 `Dependencies > Module SDK`에 해당 Python SDK를 지정합니다. `ml` 모듈이 없으면 `New Module`의 Python 항목에서 기존 `ml` 폴더와 SDK를 연결합니다. Spring용 Java 모듈의 JDK와 프로젝트 전체 SDK는 변경하지 않습니다.

Python 플러그인이나 SDK 메뉴가 없으면 설치된 IntelliJ 버전·라이선스에서 플러그인을 지원하는지 확인합니다. IDE 설정과 관계없이 아래 터미널 명령으로 서버를 실행할 수 있습니다.

`FastAPI` 전용 실행 구성을 선택했을 때 무료 모드에서 사용할 수 없다는 안내가 표시되면 해당 구성을 취소하고 아래의 **터미널 실행 방법**을 사용합니다. FastAPI 전용 실행 구성 없이도 서버를 실행할 수 있으며, 이 안내는 서버를 두 개까지만 실행할 수 있다는 뜻이 아닙니다.

일반 `Python` 실행 구성을 사용할 수 있는 환경에서는 선택적으로 `Run > Edit Configurations > + > Python`에서 다음과 같이 설정합니다.

| 설정 항목 | 값 |
| --- | --- |
| 실행 대상 | Script path 대신 Module name 선택: `uvicorn` |
| Parameters | `app.main:app --reload --port 8000` |
| Python interpreter | 위에서 연결한 `ml/.venv`의 Python |
| Working directory | 본인 PC의 프로젝트 `ml` 폴더 |

`main.py` 파일을 직접 실행하는 대신 `uvicorn` 모듈을 실행합니다. 저장한 실행 설정을 선택하고 Run을 누른 뒤 아래 상태 확인 주소로 접속합니다. 이미 터미널에서 8000 포트 서버를 실행 중이면 먼저 `Ctrl+C`로 종료합니다.

IDE 설정은 각자 PC에서 진행하며 `.idea/`는 Git에 올리지 않습니다.
참고: [JetBrains Python SDK 설정 문서](https://www.jetbrains.com/help/idea/configuring-python-sdk.html).

</details>

### 개발 서버 실행 (무료 모드 포함: 터미널 사용)

IntelliJ 아래쪽의 `Terminal` 또는 별도 PowerShell을 엽니다. 프로젝트 최상위 폴더라면 먼저 `cd ml`로 이동한 뒤 실행합니다. 이미 `ml` 폴더에 있다면 이동하지 않습니다.

```powershell
.\.venv\Scripts\python.exe -m uvicorn app.main:app --reload --port 8000
```

이 방법은 IDE의 FastAPI 전용 실행 구성이나 유료 기능을 사용하지 않습니다. 터미널을 켜둔 채 브라우저에서 확인합니다.

- 상태 확인: http://127.0.0.1:8000/health → `{"status":"ok"}`
- API 문서: http://127.0.0.1:8000/docs
- 종료: 실행 중인 터미널에서 `Ctrl+C`
- `--reload`는 파일 수정 시 서버를 다시 실행하는 개발용 옵션입니다.

### ML 파일 공유 기준

- 공유: `ml/app/`의 Python 코드, `ml/requirements.txt`, 최상위 README
- 제외: `ml/.venv/`, `__pycache__/`, `.env`, `ml/data/`의 로컬 데이터, `ml/models/`의 생성 모델
- `data/`, `models/`는 필요할 때 생성하며, 해당 폴더의 `.gitkeep`은 공유할 수 있습니다.
- 라이브러리를 변경하면 ML 가상환경에서 버전 목록을 갱신하고 변경 내용을 확인합니다.

```powershell
.\.venv\Scripts\python.exe -m pip freeze > requirements.txt
```

## 커밋 전 확인

```powershell
git status --short
git diff --cached --stat
git ls-files -ci --exclude-standard
```

마지막 명령에 출력이 없어야 이미 추적 중인 제외 대상이 없는 상태입니다.
이번 제외 설정은 과거 커밋의 비밀값을 지우지 않습니다. 이미 공유한 비밀번호나 키가 실제 사용 중이라면 폐기·재발급하고 이전 기록 정리는 별도로 진행해야 합니다.

## 협업 문서

- [문서 읽는 순서](docs/README.md)
- [전체 연결 명세](docs/creator-tax-dummy-format.md)
- [예상세액 계산 기준](docs/creator-tax-notes.md)

- [코드 컨벤션](docs/code-convention.md)
- [Git 규칙](docs/git-rule.md)
- [PR 가이드](docs/pr-guide.md)
- [API 초안](docs/api-draft.md)
