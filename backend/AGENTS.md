# 백엔드 작업 규칙

- 도메인 코드는 `com.c203.limit.domain.<domain>` 아래에 도메인 단위로 배치한다.
- 각 도메인은 필요한 `controller`, `service`, `entity` 또는 `domain`, `repository`, `dto`, `client` 또는 `event` 계층을 자신의 폴더 안에 둔다.
- `global`에는 공통 설정, 보안, 응답, 예외, 로깅만 두고 비즈니스 로직을 배치하지 않는다.
- Controller는 요청·응답 변환, Service는 유스케이스, Domain은 비즈니스 규칙을 담당한다.
- 도메인 간 Entity를 직접 공유하지 않고 Service, ID, 이벤트 또는 명시적인 인터페이스를 사용한다.
- Entity를 API에 직접 노출하지 않고 Request/Response DTO를 분리한다.
- API prefix는 `/api/v1`, 성공 응답은 공통 `ApiResponse`를 사용한다.
- 읽기 작업에는 `@Transactional(readOnly = true)`를 사용하고 트랜잭션 안에서 외부 API 호출을 오래 유지하지 않는다.
- JPA 연관관계는 단방향과 `LAZY`를 우선하며 생명주기가 완전히 같을 때만 cascade와 orphan removal을 사용한다.
- Entity에 무분별한 setter와 Lombok `@Data`를 사용하지 않고 JPA 기본 생성자는 `protected`로 선언한다.
- 운영 환경의 스키마 자동 변경을 금지하고 `ddl-auto=validate`와 migration 도구를 사용한다.
- Secret, 내부 예외, SQL, 호스트 정보를 응답이나 로그에 노출하지 않는다.
- API 변경 시 OpenAPI와 계약 테스트를 함께 수정한다.
- 기능 변경 후 정상·실패 경로의 인접 테스트를 추가하고 `gradle test`, `gradle bootJar`를 실행한다.
