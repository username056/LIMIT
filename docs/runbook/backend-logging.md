# 백엔드 로깅 규칙

운영 환경의 기본 로그 레벨은 `INFO`다. 변경되는 도메인의 `service`, `bootstrap`,
`event` 클래스는 운영 중 확인할 가치가 있는 상태 전이 또는 처리 결과를 SLF4J로
기록한다. 이 로그는 Micrometer의 `logback_events_total`과 Loki에서 함께 관측한다.

## 레벨 기준

- `INFO`: 매물 상태 변경처럼 정상적으로 완료된 중요한 비즈니스 상태 전이
- `WARN`: 복구 가능한 거부, 재시도, 외부 의존성의 일시적인 성능 저하
- `ERROR`: 요청 경계에서 한 번만 기록해야 하는 예상하지 못한 실패
- `DEBUG`/`TRACE`: 로컬 진단 정보. 운영 기본 레벨에서 수집되지 않으므로 필수 운영 로그를 대체하지 않는다.

성공한 단순 조회를 요청마다 기록하거나 Controller와 Service에서 같은 실패를 중복
기록하지 않는다. 비밀번호, 토큰, 인증 헤더, 이메일, 이름, 채팅 내용 등 Secret과
개인정보는 로그 메시지 또는 인자로 전달하지 않는다. 문자열 연결 대신 SLF4J
placeholder를 사용한다.

```java
private static final Logger log = LoggerFactory.getLogger(ListingService.class);

log.info(
        "listing status changed: listingId={}, from={}, to={}",
        listingId,
        previousStatus,
        nextStatus);
```

## CI 하네스

`scripts/check-backend-logging.sh`는 `dev` 대상 MR 또는 `dev` push에서 기준 커밋과 현재
커밋을 비교한다. 변경된 `service`, `bootstrap`, `event` 구현체에 SLF4J `log` 로거와
`log.info`, `log.warn`, `log.error` 중 하나가 모두 있어야 통과한다. DTO, Entity,
Repository, Controller는 로그 중복과 개인정보 노출을 피하기 위해 강제 대상에서
제외한다. 로거 변수명은 프로젝트 전체에서 `log`로 통일하고, 운영 로그 호출은 별도
statement로 작성한다. 주석 처리된 선언이나 호출은 하네스가 로그로 인정하지 않는다.

로컬에서는 최신 `dev`를 기준으로 실행한다.

```bash
sh scripts/check-backend-logging.sh origin/dev HEAD
sh scripts/check-backend-logging.test.sh
```
