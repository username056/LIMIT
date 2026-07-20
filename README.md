# Limit

한정판 상품의 드롭·경매·대기열·주문·결제를 지원하는 Vue 3 + Spring Boot 모노레포입니다.

## 구성

- `frontend/`: Vue 3, Vite, Vitest
- `backend/`: Java 21, Spring Boot, JPA, MongoDB, Redis
- `infra/`: Docker Compose, Nginx, 모니터링, Terraform
- `docs/`: API 계약, ADR, 운영 Runbook
- `scripts/`: CI, 빌드, 배포, 점검 스크립트

백엔드는 `com.c203.limit.<domain>` 아래에 도메인별 `controller`, `service`, `domain`, `repository`, `dto` 계층을 둡니다. 공통 응답·예외·로깅은 `global`에서 관리합니다.

## 요구 환경

- Java 21
- Node.js 22.12 이상 권장
- Docker Desktop과 Docker Compose

Gradle Wrapper가 포함되어 있어 Gradle을 별도로 설치할 필요는 없습니다.

## 로컬 실행

### 전체 인프라와 백엔드

`.env.example`을 참고해 추적되지 않는 `infra/.env`를 작성한 뒤 실행합니다.

```powershell
docker compose --env-file infra/.env -p limit-local `
  -f infra/compose.yml -f infra/compose.local.yml up -d --build
```

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

## API 계약

- 외부·내부 API prefix: `/api/v1`
- 내부 서비스 API: `/api/v1/internal/*`
- 성공 응답: `{ "data": ..., "meta": null }`
- 실패 응답: `{ "error": { "code", "message", "fieldErrors" }, "traceId" }`
- 요청 추적 헤더: `X-Trace-Id`

Swagger 계약용 Controller는 비즈니스 로직 연결 전까지 빈 `200` 응답을 반환합니다. 공통 응답은 `global.response`를 기준으로 사용합니다.

기본 확인 엔드포인트:

```text
GET /api/v1/hello
GET /api/v1/health
GET /actuator/health/readiness
GET /actuator/prometheus
```

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
- [프로젝트 작업 규칙](AGENTS.md)
