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
    "code": "STABLE_ERROR_CODE",
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

공통 성공·실패 응답은 `com.c203.limit.global.response`를 사용합니다. 페이징 응답은 `common.dto.response.PageResponse<T>`를 사용하며 제네릭 타입을 유지합니다.
검증 실패 응답에는 민감 정보가 포함될 수 있는 거절값을 담지 않고 필드명과 사유만 제공합니다.
비즈니스 예외와 Spring MVC 공통 예외는 모두 위 실패 응답 형태로 변환하며, 도메인별 오류 코드는 기능 구현 시 `ErrorCode`에 추가합니다.

## 기본 엔드포인트

| Method | Path | 설명 |
| --- | --- | --- |
| GET | `/api/v1/hello` | 프론트·백엔드 연결 확인 |
| GET | `/api/v1/health` | 애플리케이션 상태 확인 |
| GET | `/actuator/health/readiness` | 배포 Readiness 확인 |
| GET | `/actuator/prometheus` | Prometheus 메트릭 |

## Swagger 계약

- API 계약: `backend/src/main/java/com/c203/limit/<domain>/controller/*Api.java`
- Controller: `backend/src/main/java/com/c203/limit/<domain>/controller/*Controller.java`
- DTO: `backend/src/main/java/com/c203/limit/<domain>/dto/{request,response,event}/*.java`
- 공통 응답: `backend/src/main/java/com/c203/limit/global/response`
- Swagger 설정: `backend/src/main/java/com/c203/limit/swagger/config`
- 생성기: `scripts/generate-openapi-stubs.py`

API/DTO 내보내기 파일에서 106개 고유 operation을 생성합니다. 동일 method/path로 정의된 상품 action 2개는 하나의 operation으로 병합됩니다. 생성기는 공통 응답 DTO를 다시 만들지 않으며 기존 `*Controller` 구현을 덮어쓰지 않습니다.

미구현 Controller는 계약 확인을 위해 빈 `200` 응답을 반환합니다. Swagger UI와 API docs는 기본 비활성화하며 local 또는 명시적으로 허용한 환경에서만 활성화합니다.

- Swagger UI: `/swagger-ui.html`
- OpenAPI JSON: `/v3/api-docs`
