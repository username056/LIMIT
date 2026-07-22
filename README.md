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
