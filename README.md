# Limit

한정판 상품의 드롭·경매·대기열·주문·결제를 지원하는 Vue 3 + Spring Boot 모노레포입니다.

## 구성

- `frontend/`: Vue 3, Vite, Vitest
- `backend/`: Java 21, Spring Boot, JPA, MongoDB, Redis
- `infra/`: Docker Compose, Nginx, 모니터링, Terraform
- `docs/`: API 계약, ADR, 운영 Runbook
- `scripts/`: CI, 빌드, 배포, 점검 스크립트

백엔드는 `com.c203.limit.domain.<domain>` 아래에 도메인별 `controller`, `service`, `domain`, `repository`, `dto` 계층을 둡니다. 공통 API·설정·응답·예외·로깅은 `global`에서 관리합니다.

## 요구 환경

- Java 21
- Node.js 22.12 이상 권장
- Docker Desktop과 Docker Compose

Gradle Wrapper가 포함되어 있어 Gradle을 별도로 설치할 필요는 없습니다.

## 최초 설정

저장소를 처음 클론했다면 한 번만 실행합니다.

```powershell
git config core.hooksPath .githooks
```

이 설정 없이는 `.githooks/`(pre-commit Secret 검사, commit-msg 컨벤션 검사, pre-push lint/test/build)가 켜지지 않습니다. AI Hook(`.claude/settings.json`, `.codex/hooks.json`)과는 별개로 로컬 git 설정에서 직접 켜야 합니다. 자세한 내용은 [AI 활용 가이드](가이드.md#4-hook-사용법)를 참고합니다.

## 로컬 실행

### 전체 인프라와 백엔드

```powershell
.\scripts\open-platform-tools.ps1
```

`infra/.env.local`을 자동 생성하고 Docker Desktop과 컨테이너를 띄운 뒤 Swagger·Grafana를 브라우저로 엽니다. 다른 파일을 쓰려면 `-EnvFile`로 지정합니다. 운영용 `infra/.env`와 로컬 설정을 분리하며 두 파일 모두 Git에서 제외됩니다.

- Backend: `http://localhost:18080`
- Swagger: `http://localhost:18080/swagger-ui.html`
- Grafana: `http://localhost:3000`

### 프론트엔드

```powershell
cd frontend
npm ci
npm run dev
```

Frontend는 `http://localhost:5173`에서 실행되며 `/api` 요청을 로컬 백엔드로 프록시합니다.

- 새 페이지: `frontend/src/pages/`에 컴포넌트를 추가하고 `frontend/src/router/index.js`에 경로를 등록합니다.
- 공용 컴포넌트: `frontend/src/components/`(Base*)와 레이아웃(`frontend/src/layouts/`)을 우선 사용합니다.
- API 호출: 컴포넌트에서 직접 호출하지 않고 `frontend/src/api/`의 클라이언트를 거칩니다.

## API 계약

- API prefix: `/api/v1`
- 성공 응답: `{ "data": ..., "meta": null }`, 실패 응답: `{ "error": { "code", "message", "fieldErrors" }, "traceId" }`

상세 계약은 [API 문서](docs/api/README.md)를 확인합니다.

## 검증

```powershell
cd frontend
npm run lint
npm run test
npm run build
```

```powershell
cd backend
.\gradlew.bat test
.\gradlew.bat integrationTest
.\gradlew.bat bootJar
```

GitLab CI가 Secret 검사, 테스트, JaCoCo, SonarQube, 이미지 빌드와 배포 단계를 수행합니다.

## 문서

- [API 계약](docs/api/README.md)
- [모노레포 ADR](docs/adr/0001-monorepo.md)
- [배포 아키텍처 ADR](docs/adr/0002-single-ec2-blue-green.md)
- [운영 Runbook](docs/runbook/deployment.md)
- [테스트 계정 BaseInit 가이드](docs/integration/base-init-data.md)
- [프로젝트 작업 규칙](AGENTS.md)

## 팀원 로컬 테스트 설정

실제 비밀번호와 OAuth·SMTP Secret은 Git에 올리지 않는다. 백엔드용 `infra/.env.local`과 프론트용 `frontend/.env.local`은 모두 `.gitignore` 대상이다.

프론트 배포 전에는 `frontend/.env.example`의 `VITE_LEGAL_*` 값을 실제 운영자·책임자·주소·문의처·사업자 정보로 채워야 한다. 약관과 개인정보 처리방침의 법적 근거 및 외부 서비스 확인 목록은 [약관·개인정보 문서 작성 근거](docs/legal/legal-content-basis.md)를 참고한다.

DB 스키마는 Flyway가 백엔드 시작 시 적용하고 Hibernate가 `ddl-auto=validate`로 검증한다. 운영 DB 최초 전환 절차는 [Flyway DB 스키마 Runbook](docs/integration/database-schema-runbook.md)을 따른다.

### 1. 백엔드 환경 파일 준비

저장소 루트에서 아래 스크립트를 처음 실행하면 누락된 `infra/.env.local`과 로컬용 비밀값을 자동 생성하고 Docker 인프라와 백엔드를 실행한다.

```powershell
.\scripts\open-platform-tools.ps1
```

소셜 로그인이나 실제 이메일 발송을 테스트하려면 생성된 `infra/.env.local`에 필요한 공급자만 활성화하고 발급받은 값을 채운다.

```env
EMAIL_VERIFICATION_DELIVERY_ENABLED=true
FRONTEND_EMAIL_VERIFICATION_URL=http://localhost:5173/verify-email
MAIL_HOST=smtp.gmail.com
MAIL_PORT=587
MAIL_USERNAME=
MAIL_PASSWORD=
MAIL_FROM=

GOOGLE_OAUTH_ENABLED=true
GOOGLE_OAUTH_CLIENT_ID=
GOOGLE_OAUTH_CLIENT_SECRET=
GOOGLE_OAUTH_REDIRECT_URIS=http://localhost:5173/auth/callback/google

KAKAO_OAUTH_ENABLED=true
KAKAO_OAUTH_CLIENT_ID=
KAKAO_OAUTH_CLIENT_SECRET=
KAKAO_OAUTH_REDIRECT_URIS=http://localhost:5173/auth/callback/kakao

NAVER_OAUTH_ENABLED=true
NAVER_OAUTH_CLIENT_ID=
NAVER_OAUTH_CLIENT_SECRET=
NAVER_OAUTH_REDIRECT_URIS=http://localhost:5173/auth/callback/naver
```

각 공급자 콘솔에도 사용하는 로컬 Callback URL을 위 값과 정확히 동일하게 등록한다. 변수명은 단수형 `*_OAUTH_REDIRECT_URI`가 아니라 복수형 `*_OAUTH_REDIRECT_URIS`이며, 값을 바꾼 뒤에는 다음 명령으로 백엔드 컨테이너를 다시 생성한다.

```powershell
docker compose --env-file infra/.env.local -p limit-local -f infra/compose.yml -f infra/compose.local.yml up -d --build backend
```

Gmail SMTP는 일반 계정 비밀번호가 아닌 앱 비밀번호를 사용하고, `MAIL_FROM`에는 해당 계정 또는 Gmail에서 발송이 허용된 별칭을 입력한다. 메일 발송이 필요하지 않으면 `EMAIL_VERIFICATION_DELIVERY_ENABLED=false`로 둔다.

### 2. 프론트 환경 파일 준비

`frontend/.env.example`을 `frontend/.env.local`로 복사한다. Vite 환경변수는 브라우저에 공개되므로 OAuth Client Secret이나 SMTP 비밀번호를 넣으면 안 된다.

```env
VITE_API_BASE_URL=/api/v1
VITE_OAUTH_REDIRECT_BASE_URL=http://localhost:5173
VITE_TERMS_OF_SERVICE_URL=/terms/service
VITE_PRIVACY_POLICY_URL=/terms/privacy
VITE_LEGAL_OPERATOR_NAME=L1MIT 운영팀
VITE_LEGAL_REPRESENTATIVE=
VITE_LEGAL_ADDRESS=
VITE_LEGAL_CONTACT_EMAIL=privacy@l1mit.shop
VITE_LEGAL_BUSINESS_NUMBER=
VITE_LEGAL_EFFECTIVE_DATE=2026-07-22
```

배포 빌드에서는 CI/CD 변수 또는 Git에서 제외된 `frontend/.env.production.local`에 다음처럼 운영 origin을 사용한다. Vite 값은 빌드 시 정적으로 포함되므로 서버의 `infra/.env`만 수정해서는 프론트 값이 바뀌지 않으며, 변경 후 프론트 이미지를 다시 빌드·배포해야 한다.

```env
VITE_API_BASE_URL=/api/v1
VITE_OAUTH_REDIRECT_BASE_URL=https://l1mit.shop
VITE_TERMS_OF_SERVICE_URL=/terms/service
VITE_PRIVACY_POLICY_URL=/terms/privacy
```

운영 EC2의 `infra/.env`에는 백엔드가 검증할 Callback URL을 `https://l1mit.shop/auth/callback/google`처럼 공급자별 전체 경로로 넣고, Google·Kakao·NAVER 개발자 콘솔에도 정확히 같은 URL을 등록한다. `/auth/callback/`까지만 넣거나 세 공급자가 같은 Callback URL을 공유하면 안 된다.

이후 프론트를 실행한다.

```powershell
cd frontend
npm ci
npm run dev
```

- 프론트: `http://localhost:5173`
- Swagger: `http://localhost:18080/swagger-ui.html`
- API Health: `http://localhost:18080/health`

### 3. 선택적 테스트 계정 생성

회원·관리자 테스트 계정이 필요하면 [BaseInit 가이드](docs/integration/base-init-data.md)에 따라 `infra/.env.local`에 값을 넣고 백엔드를 한 번 실행한다. 계정 생성을 확인한 뒤 `BASE_INIT_ENABLED=false`와 `INITIAL_ADMIN_ENABLED=false`로 반드시 되돌린다.

상세 OAuth 설정과 공급자별 확인 항목은 [소셜 로그인 설정 가이드](docs/integration/social-login-setup.md)를 참고한다.
