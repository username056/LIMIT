# API 계약

## 공통 규칙

- 기본 prefix: `/api/v1`
- 내부 서비스 API: `/api/v1/internal/*`
- Content-Type: `application/json`
- 추적 헤더: `X-Trace-Id`
- URL은 복수형·kebab-case 자원을 사용한다.
- 상태 전이는 `cancellations`, `confirmations`, `approvals` 같은 명사형 하위 자원으로 표현한다.

성공 응답:

```json
{
  "data": {},
  "meta": null
}
```

실패 응답:

```json
{
  "error": {
    "code": "CMN001",
    "message": "사용자에게 보여 줄 메시지",
    "fieldErrors": [
      {
        "field": "email",
        "reason": "이메일 형식이 올바르지 않습니다."
      }
    ]
  },
  "traceId": "요청 추적 ID"
}
```

공통 성공·실패 응답과 페이징 응답은 `com.c203.limit.global.response`를 사용합니다. `PageResponse<T>`는 제네릭 타입을 유지합니다.
검증 실패 응답에는 민감 정보가 포함될 수 있는 거절값을 담지 않고 필드명과 사유만 제공합니다.
비즈니스 예외와 Spring MVC 공통 예외는 모두 위 실패 응답 형태로 변환하며, 도메인별 오류 코드는 기능 구현 시 `ErrorCode`에 추가합니다.

오류 코드는 `영문 접두어 + 숫자 3자리` 형식을 사용합니다. 현재는 `CMN001`부터 `CMN007`까지 공통 오류만 정의합니다. 도메인 오류는 각 기능 담당자가 구현 시 도메인을 식별할 수 있는 접두어로 추가합니다. enum 상수명은 서버 코드에서 의미를 표현하고 API의 `error.code`에는 `CMN001` 같은 고정 코드를 반환합니다. 이미 배포된 코드는 다른 의미로 재사용하지 않습니다.

## 기본 엔드포인트

| Method | Path | 설명 |
| --- | --- | --- |
| GET | `/api/v1/health` | 애플리케이션 상태 확인 |
| GET | `/actuator/health/readiness` | 배포 Readiness 확인 |
| GET | `/actuator/prometheus` | Prometheus 메트릭 |

## Swagger 계약

- API 계약: `backend/src/main/java/com/c203/limit/domain/<domain>/controller/*Api.java`
- Controller: `backend/src/main/java/com/c203/limit/domain/<domain>/controller/*Controller.java`
- DTO: `backend/src/main/java/com/c203/limit/domain/<domain>/dto/{request,response,event}/*.java`
- 공통 응답: `backend/src/main/java/com/c203/limit/global/response`
- Swagger 설정: `backend/src/main/java/com/c203/limit/global/config/swagger`

미구현 Controller는 계약 확인을 위해 빈 `200` 응답을 반환합니다. Swagger UI와 API docs는 기본 비활성화하며 local 또는 명시적으로 허용한 환경에서만 활성화합니다.

- Swagger UI: `/swagger-ui.html`
- OpenAPI JSON: `/v3/api-docs`

## OpenAPI baseline 검증

- 기준 계약은 `docs/api/openapi.json`에서 버전 관리한다.
- Merge Request에서는 실제 SpringDoc `/v3/api-docs` 결과를 추출하고 oasdiff로 기준 계약과 비교한다.
- endpoint, operation, response field 등 호환성을 깨는 변경이 발견되면 `openapi_contract` job이 실패한다.
- 의도한 계약 변경은 API 문서와 테스트를 먼저 수정한 뒤 `sh scripts/export-openapi.sh docs/api/openapi.json`을 실행한다.
- 생성된 baseline diff를 검토하고 호환 기간 또는 migration 계획과 함께 같은 MR에 포함한다.
