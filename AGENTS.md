# 프로젝트 작업 규칙

## 작업 순서

1. 이 파일과 작업 대상 폴더의 `AGENTS.md`를 먼저 읽는다.
2. 작업 시작 전 `dev`를 최신 상태로 갱신하고 목적에 맞는 작업 브랜치를 생성한다.
3. 요구사항, 관련 코드, 인접 테스트만 우선 확인한다.
4. API·아키텍처 근거가 필요할 때만 `docs/`와 Notion 원문을 조회한다.
5. 허용된 범위에서 최소 변경을 구현한다.
6. 변경한 영역의 lint, test, build를 실행하고 결과와 미검증 항목을 보고한다.

## 브랜치 전략

- `main`은 배포 가능한 안정 버전, `dev`는 개발 작업의 기준 브랜치로 사용한다.
- 기능 개발은 항상 최신 `dev`에서 새 브랜치를 생성한다.
- 브랜치 이름은 `<type>/<기능>` 형식의 소문자 `kebab-case`를 사용한다.
- 브랜치 type은 커밋 컨벤션의 허용 type과 일치시킨다. 예: `feat/order-create`, `infra/dockerhub-registry`.
- 하나의 브랜치는 하나의 목적만 가진다.
- 완료된 작업은 PR을 통해 `dev`로 병합하고 기본 merge 방식은 squash merge를 사용한다.
- 모든 기능 구현과 검증이 완료된 뒤 `dev`를 `main`으로 병합한다.
- 구조 변경 시 README, API 문서, ADR 또는 Runbook을 같은 작업에서 수정한다.

## 수정 범위와 승인

- 일반 코드, 테스트, 개발 문서는 작업 요청 범위에서 수정할 수 있다.
- 운영 설정, 배포 실행, DB migration, CI credential, IAM/RBAC 변경은 사전 승인이 필요하다.
- 실제 `.env`, Secret, 토큰, 키, 인증서, 개인정보를 읽거나 출력하거나 커밋하지 않는다.
- force push, `git reset --hard`, 광범위 삭제, 운영 DB 변경을 실행하지 않는다.
- 문서와 코드 계약이 다르면 임의로 선택하지 말고 불일치로 보고한다.

## 공통 계약

- 프론트엔드와 백엔드는 하나의 저장소에서 관리하는 모노레포로 유지한다.
- 백엔드는 하나의 Spring Boot 애플리케이션 안에서 도메인별로 분리한다.
- 각 도메인은 필요한 계층을 자신의 폴더 안에 두고 비즈니스 로직을 `global`로 옮기지 않는다.
- 도메인 간 Entity를 직접 공유하지 않고 Service, ID, 이벤트 또는 명시적인 인터페이스를 사용한다.
- 테스트 코드는 실제 도메인 경로와 유사하게 배치한다.
- API prefix는 `/api/v1`을 사용한다.
- API 경로는 복수 자원명과 `kebab-case`를 사용하고 행위는 HTTP Method로 표현한다.
- 조회는 `GET`, 생성·명령은 `POST`, 부분 수정은 `PATCH`, 전체 교체는 `PUT`, 삭제는 `DELETE`를 사용한다.
- 성공 응답은 `{ "data": ..., "meta": null }` 형태를 사용한다.
- 실패 응답은 `{ "error": { "code", "message", "fieldErrors" }, "traceId": "..." }` 형태를 사용한다.
- API 변경 시 OpenAPI와 계약 테스트를 같은 PR에서 수정한다.
- Breaking Change에는 버전 변경, 호환 기간 또는 마이그레이션 계획을 포함한다.
- `.env.example`에는 환경변수 이름과 설명만 작성하고 실제 값은 넣지 않는다.
- 로그에 비밀번호, 토큰, API Key 또는 개인정보를 남기지 않는다.
- 서비스 간 `traceId` 또는 Correlation ID를 전달하고 로그 레벨은 `INFO`를 기본으로 사용한다.
- API 또는 설정 계약을 바꾸면 테스트와 `docs/`를 함께 갱신한다.
- 생성물, IDE 개인 설정, 실제 환경변수 파일은 커밋하지 않는다.

## 네이밍

- 클래스·타입은 `PascalCase`, 메서드·변수는 `camelCase`, 상수는 `UPPER_SNAKE_CASE`를 사용한다.
- 패키지는 `lowercase`, DB 테이블·컬럼은 `snake_case`를 사용한다.
- Boolean 이름은 `is`, `has`, `can`, `should` 접두어를 사용한다.
- DTO 이름에 역할을 포함한다. 예: `CreateOrderRequest`, `OrderDetailResponse`, `PaymentEventV1`.
- Request DTO와 Response DTO를 분리하고 Entity를 API 응답으로 직접 노출하지 않는다.
- 단순 불변 DTO는 Java `record`를 우선하고 이벤트에는 명시적인 버전을 사용한다.

## 커밋 컨벤션

- 커밋 제목은 `<gitmoji> <type>: <명령형 요약>` 형식을 사용한다.
- 요약은 한 문장으로 짧고 완결되게 쓰며, `~하고`·`~하여`·`~해` 같은 연결형으로 끝내지 않는다.
- 허용 type은 `feat`, `fix`, `refactor`, `test`, `docs`, `infra`, `chore`, `perf`, `security`다.
- Gitmoji와 type의 의미를 일치시키고 하나의 커밋에는 하나의 의도만 담는다.
- Formatter 결과와 기능 변경은 가능한 한 별도 커밋으로 분리한다.
- 예: `✨ feat: 주문 생성 API 구현`, `🐛 fix: readiness 실패 시 503 반환`, `♻️ refactor: 결제 후처리 책임 분리`, `✅ test: Feign 응답 계약 테스트 추가`, `📝 docs: 실행 가이드 보완`, `🚀 infra: 카나리 배포 단계 추가`

## 테스트·품질

- 기능 추가에는 Unit 및 Controller/Slice 테스트를 작성한다.
- DB·캐시·메시지는 Integration 테스트, 외부 HTTP·이벤트는 Contract 테스트로 검증한다.
- 인증·권한·동시성은 실패 경로를 포함하고 핵심 사용자 흐름은 E2E 또는 Smoke 테스트로 확인한다.
- PR 전 test, lint, build, secret scan, 정적 분석·커버리지, 의존성 취약점, API·이벤트 계약 검사를 확인한다.
- 실행할 수 없는 검증은 성공으로 간주하지 않고 사유와 남은 위험을 보고한다.

## 기본 검증

```powershell
cd frontend
npm run lint
npm run test
npm run build
```

```powershell
cd backend
gradle test
gradle bootJar
```

완료 보고에는 변경 파일과 이유, 실행한 검증과 결과, 남은 위험과 확인하지 못한 항목을 포함한다.
