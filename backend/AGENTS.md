# 백엔드 작업 규칙

- 코드는 `com.c203.limit.<domain>` 아래에 도메인 단위로 배치한다.
- `global`에는 공통 설정, 응답, 예외, 로깅만 둔다.
- Controller는 요청·응답 변환, Service는 유스케이스, Domain은 비즈니스 규칙을 담당한다.
- Entity를 API에 직접 노출하지 않고 Request/Response DTO를 분리한다.
- 단순 불변 DTO는 Java `record`를 우선한다.
- API prefix는 `/api/v1`, 성공 응답은 공통 `ApiResponse`를 사용한다.
- Secret, 내부 예외, SQL, 호스트 정보를 응답이나 로그에 노출하지 않는다.
- 기능 변경 후 인접 테스트를 추가하고 `gradle test`, `gradle bootJar`를 실행한다.

