# 결제 API 구현 기록

## 구현 범위

- 결제 요청 생성 `POST /api/v1/payments`
- 결제 승인(confirm) `POST /api/v1/payments/{paymentId}/confirm`
- 결제 전 예약 취소 `POST /api/v1/payments/{paymentId}/cancel`
- 결제 재시도 `POST /api/v1/payments/{paymentId}/retry`
- 결제 상세 조회 `GET /api/v1/payments/{paymentId}`
- 구매자 주문 내역 목록 `GET /api/v1/orders`
- 예약 유예 시간이 지난 미결제 건 자동 만료 스케줄러

PG 웹훅 실시간 승인/거절 처리, 이상거래 테이블·관리자 화면, IP 화이트리스트, 다중 PG 추상화는
이번 범위에서 제외했다. 타임아웃 후 자동 조회·복구와 결제 실패 재시도 API는 이 시점에는 제외였지만
이후 `fix/payment-confirm-recovery`(관리자 reconcile + 만료 배치 연동)와
`feat/payment-retry-order-history`(retry API 노출)에서 추가됐다 — 아래 관련 절 참고. 실결제가
불가능한 Toss 테스트
키 단계에서 상품 선택 → 결제 요청 → Toss 결제창 → 승인 → `Payment.APPROVED`/`Listing.PAID`
(직후 자동으로 `INSPECTING` 진입, `feat/order-post-payment-flow`)
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
엔티티를 직접 참조하지 않는다. `retryAttempt()`는 이후 `POST /api/v1/payments/{paymentId}/retry`로
API에 노출됐다 — 자세한 내용과 이후 보강(method 갱신, confirmAttemptedAt 검증, reservedUntil 연장,
본인 활성 예약 자동 이어받기)은 아래 "결제 재시도·주문 내역·구매확정 후 문의" 절 참고.

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
유예(`reservation-ttl-minutes`, 기본 10분)를 다 채워야 매물을 풀어주므로, 이 API가 그 대기 없이
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
시 `Payment.approve()`(`REQUESTED -> APPROVED`)와
`ListingService.markPaid()`(`RESERVED -> PAID`, 곧바로 이어서 `INSPECTING`까지 진입 —
자세한 내용은 `product.md`의 "결제 이후 주문 흐름 완성" 절 참고)를
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

- **유예 시간**: 기본 10분이며 `limit.product.reservation-ttl-minutes` 설정으로 변경할 수 있다.
  `ListingService.reserve()`가 주입된 `Clock` 기준으로 `reservedUntil`을 계산해 `Listing.reserve()`에
  전달하고, `Listing.reserve()`는 이를 그대로 저장하면서 `reservedAt`은 별도로(시스템 시각) 기록한다.
  두 값은 서로 다른 시점에 계산되므로 `reservedUntil = reservedAt + 10분`처럼 정확히 일치한다고
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
- PG 웹훅 승인/거절 처리는 이번 범위에 없다. confirm이 클라이언트 successUrl 호출로만 트리거되는
  구조 자체는 그대로이고, 아래 "confirm 재시도·PG 대사(reconcile)" 절에서 그 유실 사례에 대한
  수동 복구 경로만 추가했다. 실시간 웹훅은 여전히 후속 과제.
- confirm 성공 경로에서 `Payment.approve()` 이후 `ListingService.markPaid()`가 실패하면 트랜잭션이
  롤백돼 Toss 승인과 우리 DB 상태가 어긋날 수 있다(위 "동작" 절 참고). 결제 취소·재조정 로직은
  아직 없다.
- ~~`PaymentService.retryAttempt()`(재시도 게이트)는 API로 열지 않았다~~ → `feat/payment-retry-order-history`
  에서 `POST /api/v1/payments/{paymentId}/retry`로 노출하고 프론트(`PurchaseFailPage`/`PurchasePage`)
  까지 연결했다. 아래 관련 절 참고.

## confirm 재시도·PG 대사(reconcile) (`fix/payment-confirm-recovery`)

결제 성공 페이지에서 confirm이 재시도되거나 새로고침되는 상황을 새 결제로 취급하지 않도록
`confirm`, 관리자 대사 API, 예약 만료 배치를 함께 보강했다.

- **이중 청구 방지(멱등 confirm)**: `orderId`·`amount`가 저장된 값과 일치하는데 결제 상태가 이미
  `APPROVED`면 Toss를 다시 부르지 않고 기존 승인 결과를 그대로 반환한다. 단 `paymentKey`까지
  승인 기록(`Payment.providerTransactionId`)과 일치해야 하며, 다르면 위조 가능성으로 보고
  `PAYMENT_ALREADY_CONFIRMED_MISMATCH`(`PAY016`, 409)로 거부한다.
- **confirm 시도 흔적**: `Payment.confirmAttemptedAt`을 Toss confirm 호출 직전에 기록한다
  (`V20260815__add_payment_confirm_attempted_at_column.sql`). confirm을 시도한 적 없는 순수
  이탈 건과, 서버가 실제로 confirm을 불렀지만 응답을 받지 못한 건을 구분하는 용도다.
- **관리자 PG 대사 API**: `POST /api/v1/admin/payments/{paymentId}/reconcile`
  (`PaymentService.reconcile()`)이 REQUESTED로 남은 결제를 `TossPaymentClient.findByOrderId()`로
  재조회한다. Toss가 `DONE`이고 금액·orderId가 일치하면 승인·매물을 PAID(곧바로 INSPECTING까지)로
  복구하고(`RECOVERED`),
  이미 REQUESTED가 아니거나 Toss에 승인 기록이 없으면 아무 것도 바꾸지 않는다(`NO_ACTION`).
  금액·orderId가 어긋나면 자동 복구하지 않고 `PAYMENT_RECONCILE_MISMATCH`(`PAY017`, 409)로
  운영자 확인을 요구하고, Toss 조회 자체가 일시 실패하면 `PAYMENT_RECONCILE_RETRYABLE`
  (`PAY018`, 503)을 반환한다.
- **예약 만료 배치 안전화**: `PaymentReservationExpirationService.expireOne()`은 대상 결제에
  `confirmAttemptedAt`이 있으면 만료 전에 먼저 `reconcile()`을 호출한다. 복구되면 만료시키지 않고
  `RECOVERED`로 집계하며, `NO_ACTION`이면 기존대로 즉시 만료한다. confirm을 시도한 적 없는 건은
  이 조회 없이 바로 만료해 배치가 Toss 가용성에 불필요하게 묶이지 않게 한다.
- **거절된 결제의 즉시 예약 해제**: Toss가 confirm을 명확히 거절(카드 거절 등, `PAYMENT_CONFIRM_REJECTED`
  /`PAY012`)하면 `Payment.fail()`과 별도 트랜잭션으로 `listingService.cancelReservation()`을 호출해
  매물을 곧바로 `ON_SALE`로 되돌린다. 거절은 승인 여부가 불명확한 상태가 아니라 Toss가 확정적으로
  끝낸 시도라 예약 TTL(기본 10분)이 끝날 때까지 매물을 묶어둘 이유가 없다 — 다른 결제수단으로 새
  결제를 시작하거나 다른 구매자가 살 수 있게 한다. 예약 해제 자체가 실패해도(TTL 만료로 스케줄러가
  먼저 처리한 경우 등) 로그만 남기고 `Payment.FAILED`, `PAYMENT_CONFIRM_REJECTED` 응답은 그대로
  유지한다. 승인 여부가 불명확한 `PAYMENT_ALREADY_CONFIRMED_MISMATCH`/`PAYMENT_RECONCILE_MISMATCH`
  등은 대상이 아니며 예약을 그대로 둔다.
- **대사 복구 전용 매물 전이(`Listing.markPaidRecoveredFromPg`)**: `reconcile()`이 대상으로 삼는
  결제는 정의상 예약 TTL이 이미 지난 REQUESTED 건이다. 그런데 일반 결제 확정이 쓰는
  `Listing.markPaid()`는 `reservedUntil`이 지났으면 거부하도록 설계돼 있어(다른 구매자에게 넘어간
  예약을 덮어쓰지 않기 위한 안전장치), 그대로 재사용하면 "복구가 필요한 경우일수록 복구가 실패하는"
  자기모순이 생긴다 — Payment는 APPROVED인데 Listing은 RESERVED에 갇히고, 이후 배치는
  `findByListingIdAndStatus(..., REQUESTED)`가 더 이상 이 건을 찾지 못해 영영 복구도 만료도 못 하게
  된다. 그래서 `reconcile()`은 TTL 검증 없이 buyerId 일치만 확인하는 별도 전이
  `ListingService.markPaidRecoveredFromPg()` /
  `Listing.markPaidRecoveredFromPg(buyerId, now)`를 쓴다. 일반 `confirm()`의 `markPaid()`는
  TTL 검증을 그대로 유지한다 — 체크 시점과 확정 시점 사이의 경합을 막는 이중 방어선이라 완화하면
  안 된다. 매물 반영이 그래도 실패하면(그 사이 다른 구매자가 재예약한 경우 등) 로그만 남기고
  `PAYMENT_CONFIRM_RESERVATION_INVALID`로 승격하는 건 `confirm()`의 매물 반영 실패 처리와 동일하다
  (`PaymentService.applyListingPaidTransitionOrEscalate`로 공통화).

## 결제 재시도·주문 내역·구매확정 후 문의 (`feat/payment-retry-order-history`)

결제창 이탈·취소 후 같은 구매자가 다시 결제할 수 있게 하고, 주문 내역과 구매확정 후 판매자 문의를
실제 데이터로 연결했다.

- **`POST /api/v1/payments/{paymentId}/retry`**: 서비스 계층에는 이미 있던
  `PaymentService.retryAttempt()`를 API로 노출했다. 예약이 여전히 활성 상태면 `attemptNo`를 올리고
  새 `providerOrderId`를 발급해 같은 결제 요청을 재사용한다. API로 노출하며
  `payment.getStatus() != REQUESTED`를 소유자 확인 직후 명시적으로 검증하도록 보강했다 —
  `isReservationActive()`만으로는 매물 반영 실패로 Payment는 APPROVED/FAILED로 끝났는데 Listing만
  RESERVED로 남는 갈라진 상태에서 재시도가 통과할 수 있었다(문서상 계약은 "이미 승인·거절된 결제는
  재시도 불가"였지만 코드가 이를 완전히 보장하지 못했다).
- **retry 요청에 `method`를 받는다(`RetryPaymentRequest`)**: 재시도 결제창에서 사용자가 결제 수단을
  바꿀 수 있는데(예: CARD로 생성된 결제를 TOSSPAY로 재시도), `retryAttempt()`가 method를 받지
  않으면 Toss에는 새로 고른 수단으로 요청하면서 DB의 `Payment.method`는 최초 생성 시점 값으로
  남아 실제 처리 수단과 어긋난다. `Payment.retry(PaymentMethod method)`가 매번 method를 갱신하도록
  바꿔 이 불일치를 없앴다.
- **`PurchaseFailPage` → `PurchasePage` 재시도 연동**: `cancelPayment()`가 성공하면(예약 해제됨)
  "다시 시도하기"는 그대로 `/purchase/{productId}`로 보내 새 결제를 만든다. `cancelPayment()`가
  실패하면(네트워크 오류 등) 예약이 여전히 이 구매자 앞으로 살아있을 수 있어, `retryPaymentId` 쿼리
  파라미터를 붙여 같은 `/purchase/{productId}`로 보낸다. `PurchasePage`는 이 파라미터가 있으면
  `createPayment()` 대신 `retryPayment()`를 호출해 새 예약을 만들지 않는다 — 이미 `RESERVED`인
  매물에 새로 `reserve()`를 시도하면 `LISTING_NOT_ON_SALE`로 거부되기 때문이다.
- **`GET /api/v1/orders`**: `OrderQueryService`가 구매자의 결제 내역(`REQUESTED` 제외, 최신순)을
  조회하고, 매물 표시 정보(상품명·상태·대표 이미지)는 `ListingOrderSummaryReader`(payment 도메인 안,
  raw JDBC)로 별도 조회해 product 도메인 Entity를 직접 참조하지 않는다 — `ListingChatReader`,
  `ExpiredReservationCandidateReader`와 같은 방식이다. 대표 이미지 URL은 `MediaUrlResolver`(product
  도메인의 인프라 유틸리티, Entity 아님)를 재사용해 해석한다. `MyOrdersPage`의 더미 데이터를 이
  API 응답으로 교체했다.
- **결제 완료 후 판매자 문의가 막히던 문제**: `ChatRoomService`의 신규 채팅방 생성은 매물이
  `ON_SALE`일 때만 허용했다. 결제까지 마친 구매자가 주문 내역에서 처음 문의를 보내면(사전에 채팅한
  적 없는 경우) 매물이 이미 `PAID`/`INSPECTING`/`CONFIRMED`/`SETTLED`라 `CHAT_ROOM_CREATION_NOT_ALLOWED`
  로 거부됐다. `ListingChatReader`가 매물의 `buyer_id`도 함께 조회하도록 넓히고, 이 네 상태에서는
  요청자가 실제 구매자(`listing.buyerId()`와 일치)일 때만 생성을 허용하도록 `validateCreation()`을
  수정했다 — 관계없는 제3자가 결제 완료 매물에 채팅을 거는 것은 여전히 막는다. `CANCELLED`·`HIDDEN`
  등 나머지 상태는 그대로 거부한다.
- **`confirmAttemptedAt`이 찍힌 REQUESTED 결제는 retry도 막는다**: confirm 호출은 나갔는데 서버가
  결과를 확정하지 못한 애매한 상태에서 `retryAttempt()`가 새 `providerOrderId`를 발급해 새 결제창을
  열도록 허용하면, 원래 시도가 실제로는 Toss에서 승인됐을 경우 이중 청구로 이어진다. 이 상태는
  재시도가 아니라 관리자 `reconcile()`로만 풀어야 하므로 `status == REQUESTED` 확인 다음에
  `confirmAttemptedAt != null`이면 `PAYMENT_RETRY_NOT_ALLOWED`로 거부한다.
- **`PurchasePage`의 retry 대상 검증**: `retryPaymentId`는 URL 쿼리라 사용자가 직접
  `/purchase/다른상품?retryPaymentId=내결제ID`처럼 바꿔 들어올 수 있다. 백엔드는 소유자 확인만
  하고 "화면에 보이는 상품"과 "실제 결제 대상"이 같은지는 보장하지 않으므로, 프론트가
  `GET /api/v1/payments/{paymentId}`로 재시도 대상 결제를 조회해 `listingId`가 현재 route의
  `productId`와 다르면 결제 자체를 진행하지 못하게 막는다(주문 요약·결제 버튼 대신 안내 카드만
  보여줌).
- **`POST /api/v1/payments`가 본인의 활성 예약을 자동으로 이어받는다**: `retryPaymentId` 기반 재시도는
  Toss `failUrl`을 거쳐 `PurchaseFailPage`로 돌아온 경우만 커버한다. 상품 상세에서 "구매하기"를
  다시 누르는 일반 경로는 매번 새 `idempotencyKey`(`crypto.randomUUID()`)를 만들기 때문에 기존
  idempotencyKey 조회로는 못 잡고, 매물이 본인 예약으로 `RESERVED`라 `LISTING_NOT_ON_SALE`로
  막혔다. `PaymentService.createPayment()`가 idempotencyKey로 못 찾으면
  `findTopByListingIdAndBuyer_IdAndStatusOrderByRequestedAtDesc()`로 같은 매물·같은 구매자의
  REQUESTED 결제를 조회하고, 있으면 새로 예약·생성하지 않고 `continueRequestedPayment()`로
  이어간다. 이 메서드는 `retryAttempt()`(명시적 `/payments/{paymentId}/retry`)와 검증·갱신 로직을
  그대로 공유한다 — 두 경로가 서로 다른 규칙으로 갈라지지 않게 하기 위함이다: `status ==
  REQUESTED` 확인, `confirmAttemptedAt` 확인(있으면 절대 이어받지 않고 `PAYMENT_RETRY_NOT_ALLOWED`),
  예약 활성 확인, `ListingService.renewReservationForBuyer()`로 `reservedUntil`을 지금부터 다시
  10분 연장, `Payment.retry(method)`로 결제수단 갱신까지 동일하다. 이 변경으로 명시적 retry API도
  호출할 때마다 예약 유예 시간이 새로 연장되도록 함께 바뀌었다(기존에는 원래 유예 시간 안에서만
  재시도를 허용했다).
  같은 매물·구매자에 정상 흐름이라면 REQUESTED가 하나여야 하지만, 과거 데이터 이상으로 여러 건이
  남아 있을 가능성까지 고려해 `findTopBy...`로 최신 1건만 가져온다 — DB 유니크 제약까지는 MySQL
  partial unique 이슈가 있어 이번 범위에서 다루지 않았다.

## Swagger 그룹

`application.yml`의 `springdoc.group-configs`에 `08-payment` 그룹을 추가했다
(`com.c203.limit.domain.payment.controller`). 태그는 `08. 결제`를 사용한다.
