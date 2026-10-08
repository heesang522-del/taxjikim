# taxjikim
khacademy final team project taxjikim - React / Spring Boot / ML

## GitHub에 올리는 파일과 로컬 파일

- 최상위 `.gitignore`는 같은 저장소의 `front`, `back`, `ml`에 함께 적용됩니다. 각 폴더의 기존 `.gitignore`도 유지합니다.
- 소스 코드, mock 예시 데이터, `package.json`, `package-lock.json`, Gradle Wrapper, 설정 예시 파일은 공유합니다.
- 비밀 설정, 설치 파일, 빌드 결과, IDE 개인 설정, 로컬 백업, Python 가상환경, 로컬 ML 데이터·학습 결과는 제외합니다.
- `git rm --cached`로 추적을 해제한 파일은 PC에 그대로 남습니다. Git에서 보이는 삭제 표시는 저장소에서 제외한다는 뜻입니다.

## 백엔드 로컬 설정 준비

- `back/src/main/resources/application.example.yml`을 같은 폴더의 `application.yml`로 복사합니다.
- 별도 post 모듈은 `back/src/main/java/com/kh/back/post/src/main/resources/application.example.properties`를 같은 폴더의 `application.properties`로 복사합니다.
- 예시에 적힌 `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`와 필요한 S3/AWS 환경 변수를 실행 터미널 또는 IDE 실행 설정에 지정합니다.
- 실제 `application.yml`과 `application.properties`는 Git에서 제외됩니다. 예시 파일에 실제 비밀번호나 키를 적지 마세요.
- `.env` 파일은 Git에서 제외하지만 Spring Boot가 자동으로 읽도록 추가 설정한 것은 아닙니다.

## 커밋 전 확인

```powershell
git status --short
git diff --cached --stat
git ls-files -ci --exclude-standard
```

마지막 명령에 출력이 없어야 이미 추적 중인 제외 대상이 없는 상태입니다.
이번 제외 설정은 과거 커밋의 비밀값을 지우지 않습니다. 이미 공유한 비밀번호나 키가 실제 사용 중이라면 폐기·재발급하고 이전 기록 정리는 별도로 진행해야 합니다.
