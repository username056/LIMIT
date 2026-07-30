# 결제 API 구현 기록

## 구현 범위

- 결제 요청 생성 `POST /api/v1/payments`
- 결제 승인(confirm) `POST /api/v1/payments/{paymentId}/confirm`
- 결제 전 예약 취소 `POST /api/v1/payments/{paymentId}/cancel`
- 결제 상세 조회 `GET /api/v1/payments/{paymentId}`
- 예약 유예 시간이 지난 미결제 건 자동 만료 스케줄러

PG 웹훅 승인/거절 처리, 타임아웃 후 자동 조회·복구, 결제 실패 재시도 API, 이상거래 테이블·관리자
화면, IP 화이트리스트, 다중 PG 추상화는 이번 범위에서 제외했다. 실결제가 불가능한 Toss 테스트
키 단계에서 상품 선택 → 결제 요청 → Toss 결제창 → 승인 → `Payment.APPROVED`/`Listing.PAID`
전환까지의 수직 흐름을 먼저 완성하는 것을 목표로 한다. 이번 작업은
`V20260728__add_reservation_deadline_and_payment_event_id_columns.sql` 등 선행 스키마 PR에서
이미 추가된 `payment`, `listing` 컬럼 위에 최소한의 생성·승인·조회 유스케이스만 올린다.

## 동작

`POST /api/v1/payments`는 본인 매물 결제 시도(`SELF_PURCHASE_NOT_ALLOWED`)를 먼저 검증한 뒤,
매물을 예약(`Listing.reserve`, `ON_SALE -> RESERVED`)하고 결제 요청 레코드를 생성한다. 예약과
결제 레코드 생성은 하나의 트랜잭션으로 묶여 있어 이후 단계(회원 조회 등)가 실패해도 예약은 함께
롤백된다.

결제 금액은 클라이언트 입력을 신뢰하지 않고 항상 `Listing.price`에서 가져온다.

`Payment`는 Toss 결제창에 전달할 `providerOrderId`를 발급한다(`PAY-{paymentId}-{attemptNo}`).
`paymentId`는 `IDENTITY` 채번이라 최초 insert 시점엔 값이 없어, 저장(`save`) 직후 같은
트랜잭션 안에서 `Payment.assignProviderOrderId()`를 호출해 채운다. 컬럼은 이 때문에 nullable로
두었다 — 커밋 전 짧은 순간 DB 값이 NULL인 행이 존재하지만 트랜잭션 격리로 다른 트랜잭션에는
보이지 않고, 실패 시 트랜잭션 전체가 롤백돼 NULL이 커밋되는 경우는 없다. UNIQUE 제약은 MySQL이
NULL 다중 값을 허용하므로 이 일시 상태와 충돌하지 않는다.

`PaymentResponse.providerOrderId`로 노출되며 `attemptNo`가 1일 때의 최초 발급값이다. 같은
결제창 세션을 유지하는 동안은 이 값을 그대로 재사용하고, 새 결제창을 열 때만
`PaymentService.retryAttempt()`가 `attemptNo`를 올리고 새 providerOrderId를 발급한다. 이때 매물
예약(`Listing.reservedUntil`)이 이미 만료됐으면 `PAYMENT_RETRY_NOT_ALLOWED`(`PAY007`, 409)로
거부해 만료된 매물에 새 providerOrderId가 계속 발급되는 것을 막는다 — 이 확인은
`ListingService.isReservationActive(listingId, buyerId)`를 통해서만 하고 `Payment`가 `Listing`
엔티티를 직접 참조하지 않는다. `retryAttempt()`는 이번 PR에서 API로는 아직 열지 않았다 — Toss
confirm/웹훅 연동(후속 PR)에서 실제 재시도 진입점이 정해지면 그때 컨트롤러에 연결한다.

Toss confirm 요청에 쓸 멱등키는 이 `idempotencyKey`(결제 생성 요청 중복 방지용)와 별개로,
confirm 연동 시 `payment-confirm-{paymentId}-{attemptNo}` 형태로 결정적으로 생성한다(별도 컬럼
불필요). 재시도로 새 시도가 열리면 새 값이 나오므로 이전 승인 결과가 실수로 재사용되지 않는다.

`idempotencyKey`는 클라이언트가 생성해 전달하며, 같은 구매자가 같은 키로 같은 매물·결제수단을
재요청하면 새로 예약을 시도하지 않고 기존 결제 요청을 그대로 반환한다
(`PaymentRepository.findByBuyerIdAndIdempotencyKey`로 구매자 범위까지 확인). 다른 구매자가 같은
키를 재사용하거나, 같은 구매자가 같은 키로 매물·결제수단이 다른 요청을 보내면
`IDEMPOTENCY_KEY_CONFLICT`(`PAY004`, 409)를 반환한다. 이 유니크 제약 위반은 재시도해도 해소되지
않는 영구적 충돌일 수 있어(다른 구매자가 이미 그 키를 점유), 감지 즉시 재조회로 복구를 시도하고
내 것이 아니면 남은 재시도를 소진하지 않고 바로 `IDEMPOTENCY_KEY_CONFLICT`로 응답한다.

`POST /api/v1/payments/{paymentId}/cancel`은 Toss 결제창 진입 전(`REQUESTED`) 단계에서 구매자가
명시적으로 취소할 때 쓴다. 결제창을 취소하거나 브라우저를 닫아도 자동 만료 스케줄러가 예약
유예(`reservation-ttl-minutes`, 기본 30분)를 다 채워야 매물을 풀어주므로, 이 API가 그 대기 없이
`Payment.CANCELLED` 전환과 `Listing.cancelReservation()`(`RESERVED -> ON_SALE`)을 한 트랜잭션으로
묶어 즉시 처리한다 — 스케줄러는 이 호출이 유실됐을 때의 최종 안전망으로 계속 남는다. 이미
`CANCELLED`·`EXPIRED`인 결제는 같은 결과를 그대로 반환하고(멱등), `APPROVED`·`FAILED`처럼 이미
진행된 결제는 `PAYMENT_NOT_CANCELLABLE`(`PAY015`, 409)로 거부해 환불 흐름으로 유도한다.
프론트엔드는 `PurchaseFailPage`가 마운트될 때 `paymentId`가 있으면 이 API를 호출하고, 실패해도
화면에는 영향을 주지 않는다(스케줄러가 안전망이므로).

동일 매물에 대한 서로 다른 구매자의 동시 요청은 `Listing`의 낙관적 락(`payment_version` 아님,
`Listing.version`) 경합으로 이어질 수 있어, 실패한 트랜잭션을 버리고 새 트랜잭션으로 최대 3회까지
재시도한다(`PaymentService.request`). 재시도 끝에 이긴 쪽은 매물을 예약하고, 진 쪽은 재조회 시
매물이 이미 `RESERVED` 상태라 `LISTING_NOT_ON_SALE`(409)을 받는다. 3회 재시도로도 락 경합이
해소되지 않으면(진짜 DB 데드락 등) 원 예외를 그대로 노출하지 않고
`PAYMENT_REQUEST_CONFLICT`(`PAY005`, 409)로 정리해 응답 계약을 지킨다.

`GET /api/v1/payments/{paymentId}`는 결제를 요청한 본인만 조회할 수 있다
(`PAYMENT_ACCESS_DENIED`).

`POST /api/v1/payments/{paymentId}/confirm`은 Toss 결제창에서 승인된 결제(`paymentKey`, `orderId`,
`amount`)를 서버가 최종 확정한다. 클라이언트가 보낸 `orderId`·`amount`는 신뢰하지 않고 저장된
`Payment.providerOrderId`, `requestedAmount`와 대조하며 다르면 각각 `PAYMENT_ORDER_ID_MISMATCH`
(`PAY008`, 400), `PAYMENT_AMOUNT_MISMATCH`(`PAY009`, 400)로 거부한다. 결제 상태가 `REQUESTED`가
아니면 Toss API를 호출하지 않고 바로 `PAYMENT_NOT_CONFIRMABLE`(`PAY010`, 409)로 거부한다(중복
승인·재시도 중 상태가 바뀐 요청 방지). Toss confirm 호출에는 시도(`attemptNo`)별로 고정된
`payment-confirm-{paymentId}-{attemptNo}` 멱등키를 사용해 같은 시도를 여러 번 승인 요청해도 Toss가
같은 결과를 반환하도록 한다.

Toss 응답이 실패면 `TossPaymentClientException.isRetryable()`로 갈린다 — 5xx나 일부 일시적 코드는
`Payment` 상태를 건드리지 않고 `PAYMENT_CONFIRM_RETRYABLE`(`PAY011`, 503)만 반환해 클라이언트가
같은 멱등키로 다시 confirm을 호출하게 한다. 그 외(카드 거절 등)는 `Payment.fail()`로 `FAILED`
전환 후 Toss 메시지를 그대로 담아 `PAYMENT_CONFIRM_REJECTED`(`PAY012`, 422)를 반환한다. 승인 성공
시 `Payment.approve()`(`REQUESTED -> APPROVED`)와 `ListingService.markPaid()`(`RESERVED -> PAID`)를
같은 트랜잭션에서 호출한다 — Toss 승인 후 `markPaid()`가 실패(예: 예약이 이미 다른 경로로
바뀐 경우)하면 트랜잭션이 롤백되어 우리 DB는 `REQUESTED`로 남지만 Toss 쪽은 이미 승인된 상태로
남는 불일치가 생길 수 있다. 웹훅·조회 기반 재조정은 아직 없으므로 후속 과제로 남긴다.

## 도메인 경계

결제 도메인은 매물 상태 전이를 직접 다루지 않고 기존 `ListingService.reserve(listingId, buyerId)`를
그대로 재사용한다. 본인 매물 여부 확인에는 상태 전이가 없는 `ListingService.get(listingId)`를
별도로 추가해 예약 전에 먼저 조회한다. `LISTING_NOT_FOUND`, `LISTING_NOT_ON_SALE` 오류는
`ListingService`가 이미 정의한 것을 그대로 사용하고, 결제 도메인은 `PAYMENT_NOT_FOUND`,
`PAYMENT_ACCESS_DENIED`, `SELF_PURCHASE_NOT_ALLOWED`, `IDEMPOTENCY_KEY_CONFLICT`,
`PAYMENT_REQUEST_CONFLICT`, `PAYMENT_NOT_EXPIRABLE`, `PAYMENT_RETRY_NOT_ALLOWED`,
`PAYMENT_ORDER_ID_MISMATCH`, `PAYMENT_AMOUNT_MISMATCH`, `PAYMENT_NOT_CONFIRMABLE`,
`PAYMENT_CONFIRM_RETRYABLE`, `PAYMENT_CONFIRM_REJECTED`(`PAY001~012`)만 새로 추가했다. confirm은
성공 시에만 `ListingService.markPaid(listingId)`로 매물 상태 전이를 위임한다.

예약 만료 스케줄러도 같은 원칙을 따른다 — product 도메인의 `Listing`/`ListingRepository`를
직접 참조하지 않고, payment 도메인 안의 `ExpiredReservationCandidateReader`가 listing 테이블을
읽기 전용으로 조회하고 매물 상태 전이는 여전히 `ListingService.expireReservation()`에 위임한다.

## 예약 만료 스케줄러

결제 요청 생성 시 매물이 `RESERVED`로 넘어가지만 결제가 끝까지 완료되지 않으면(PG 연동이 아직
없어 현재는 승인 자체가 불가능) 매물이 영구히 예약 상태로 묶일 수 있다. 이를 정리하기 위해
`PaymentReservationExpirationScheduler`가 주기적으로 유예 시간이 지난 예약을 찾아 되돌린다.

- **유예 시간**: 기본 30분이며 `limit.product.reservation-ttl-minutes` 설정으로 변경할 수 있다.
  `ListingService.reserve()`가 주입된 `Clock` 기준으로 `reservedUntil`을 계산해 `Listing.reserve()`에
  전달하고, `Listing.reserve()`는 이를 그대로 저장하면서 `reservedAt`은 별도로(시스템 시각) 기록한다.
  두 값은 서로 다른 시점에 계산되므로 `reservedUntil = reservedAt + 30분`처럼 정확히 일치한다고
  가정하면 안 된다 — 만료 판정에는 `reservedUntil`만 쓰인다.
- **대상 조회**: `status = RESERVED AND reserved_until < now() AND deleted_at IS NULL`인 매물을
  `ExpiredReservationCandidateReader`(payment 도메인, raw JDBC)가 조회한다. product 도메인의
  Entity/Repository를 직접 참조하지 않기 위해 `ListingChatReader`와 같은 방식을 따른다.
- **상태 전환**: 대상 매물마다 대응하는 `REQUESTED` 상태 결제를 찾아
  `Payment.expire()`(`REQUESTED -> EXPIRED`)로 전환하고, `ListingService.expireReservation()`으로
  매물을 `RESERVED -> ON_SALE`로 되돌린다. `Payment.expire()`는 `REQUESTED`가 아닌 결제에는
  `PAYMENT_NOT_EXPIRABLE`(`PAY006`, 409)을 던져 승인된 결제가 실수로 만료되지 않도록 막는다.
- **배치 크기·주기**: 한 번에 최대 100건, 기본 1분 주기(`limit.payment.reservation-expiration.*`
  프로퍼티로 조정 가능). 대상 하나마다 독립 트랜잭션으로 처리해 한 건의 실패·경합이 나머지를
  막지 않는다.
- **중복 실행·실패 처리**: 이미 다른 실행이 처리한 매물(상태가 더 이상 `RESERVED`가 아니거나
  결제가 이미 다른 상태로 바뀐 경우)은 `BusinessException`이 전파되어 해당 건만 건너뛰고 나머지는
  계속 처리한다. 대응하는 `REQUESTED` 결제가 없는 예약(비정상 데이터)은 `SKIPPED_NO_PAYMENT`로
  집계만 하고 예약은 그대로 둔다 — 원인 조사가 필요한 상태로 남겨 자동으로 지우지 않는다.
- **EXPIRED 상태 의미**: PG 거절 등 결제 자체의 실패(`FAILED`)와 구분되는, 유예 시간 안에
  결제가 완료되지 않아 시스템이 강제로 종료한 상태다.

## 남은 위험 · 확인 필요 사항

- 매물을 `ON_SALE`에서 `RESERVED`로 옮기는 별도의 "예약" 진입점이 아직 없다. 지금은 결제 요청
  생성이 예약과 결제를 한 번에 처리하는 유일한 경로다. 채팅 등에서 별도 예약 버튼이 추가되면
  이 가정을 다시 검토해야 한다.
- 예약 만료 스케줄러는 모든 배포 인스턴스(Blue/Green 동시 기동 포함)에서 실행된다. 낙관적 락으로
  최종 데이터는 보호되지만 중복 처리 시도와 경고 로그가 발생할 수 있다 — 인스턴스 단일화 또는
  분산 락은 후속 과제.
- `(status, reserved_until)` 복합 인덱스가 아직 없다. 데이터가 늘면 스케줄러 조회가 매물 테이블을
  풀스캔할 수 있어 운영 전 인덱스 마이그레이션이 필요하다.
- PG 웹훅 승인/거절 처리는 이번 범위에 없다. confirm은 클라이언트가 successUrl로 돌아와 명시적으로
  호출해야만 승인되므로, 결제창을 닫거나 브라우저가 successUrl 진입 전에 종료되면 Toss는 승인됐지만
  우리 시스템은 `REQUESTED`로 남는 상태가 생길 수 있다. 웹훅 또는 주기적 조회 복구는 후속 과제.
- confirm 성공 경로에서 `Payment.approve()` 이후 `ListingService.markPaid()`가 실패하면 트랜잭션이
  롤백돼 Toss 승인과 우리 DB 상태가 어긋날 수 있다(위 "동작" 절 참고). 결제 취소·재조정 로직은
  아직 없다.
- `PaymentService.retryAttempt()`(재시도 게이트)는 이번에도 API로 열지 않았다 — 결제창을 다시 열 때
  같은 `providerOrderId`를 재사용할지, 새 시도를 발급할지는 프론트 재시도 흐름이 정해지면 연결한다.

## Swagger 그룹

`application.yml`의 `springdoc.group-configs`에 `08-payment` 그룹을 추가했다
(`com.c203.limit.domain.payment.controller`). 태그는 `08. 결제`를 사용한다.
