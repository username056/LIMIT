# 결제 API 구현 기록

## 구현 범위

- 결제 요청 생성 `POST /api/v1/payments`
- 결제 상세 조회 `GET /api/v1/payments/{paymentId}`
- 예약 유예 시간이 지난 미결제 건 자동 만료 스케줄러

PG 웹훅 승인/거절 처리, 결제 재시도, 환불, 정산은 담당 범위에서 제외했다. 이번 작업은
`V20260728__add_reservation_deadline_and_payment_event_id_columns.sql` 등 선행 스키마 PR에서
이미 추가된 `payment`, `listing` 컬럼 위에 최소한의 생성·조회 유스케이스만 올린다.

## 동작

`POST /api/v1/payments`는 본인 매물 결제 시도(`SELF_PURCHASE_NOT_ALLOWED`)를 먼저 검증한 뒤,
매물을 예약(`Listing.reserve`, `ON_SALE -> RESERVED`)하고 결제 요청 레코드를 생성한다. 예약과
결제 레코드 생성은 하나의 트랜잭션으로 묶여 있어 이후 단계(회원 조회 등)가 실패해도 예약은 함께
롤백된다.

결제 금액은 클라이언트 입력을 신뢰하지 않고 항상 `Listing.price`에서 가져온다.

`idempotencyKey`는 클라이언트가 생성해 전달하며, 같은 구매자가 같은 키로 같은 매물·결제수단을
재요청하면 새로 예약을 시도하지 않고 기존 결제 요청을 그대로 반환한다
(`PaymentRepository.findByBuyerIdAndIdempotencyKey`로 구매자 범위까지 확인). 다른 구매자가 같은
키를 재사용하거나, 같은 구매자가 같은 키로 매물·결제수단이 다른 요청을 보내면
`IDEMPOTENCY_KEY_CONFLICT`(`PAY004`, 409)를 반환한다. 이 유니크 제약 위반은 재시도해도 해소되지
않는 영구적 충돌일 수 있어(다른 구매자가 이미 그 키를 점유), 감지 즉시 재조회로 복구를 시도하고
내 것이 아니면 남은 재시도를 소진하지 않고 바로 `IDEMPOTENCY_KEY_CONFLICT`로 응답한다.

동일 매물에 대한 서로 다른 구매자의 동시 요청은 `Listing`의 낙관적 락(`payment_version` 아님,
`Listing.version`) 경합으로 이어질 수 있어, 실패한 트랜잭션을 버리고 새 트랜잭션으로 최대 3회까지
재시도한다(`PaymentService.request`). 재시도 끝에 이긴 쪽은 매물을 예약하고, 진 쪽은 재조회 시
매물이 이미 `RESERVED` 상태라 `LISTING_NOT_ON_SALE`(409)을 받는다. 3회 재시도로도 락 경합이
해소되지 않으면(진짜 DB 데드락 등) 원 예외를 그대로 노출하지 않고
`PAYMENT_REQUEST_CONFLICT`(`PAY005`, 409)로 정리해 응답 계약을 지킨다.

`GET /api/v1/payments/{paymentId}`는 결제를 요청한 본인만 조회할 수 있다
(`PAYMENT_ACCESS_DENIED`).

## 도메인 경계

결제 도메인은 매물 상태 전이를 직접 다루지 않고 기존 `ListingService.reserve(listingId, buyerId)`를
그대로 재사용한다. 본인 매물 여부 확인에는 상태 전이가 없는 `ListingService.get(listingId)`를
별도로 추가해 예약 전에 먼저 조회한다. `LISTING_NOT_FOUND`, `LISTING_NOT_ON_SALE` 오류는
`ListingService`가 이미 정의한 것을 그대로 사용하고, 결제 도메인은 `PAYMENT_NOT_FOUND`,
`PAYMENT_ACCESS_DENIED`, `SELF_PURCHASE_NOT_ALLOWED`, `IDEMPOTENCY_KEY_CONFLICT`,
`PAYMENT_REQUEST_CONFLICT`, `PAYMENT_NOT_EXPIRABLE`(`PAY001~006`)만 새로 추가했다.

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
- PG 연동, 웹훅 승인(`Payment.approve`), 결제 실패·재시도(`Payment.retry`)는 이 PR에서 다루지
  않았다.

## Swagger 그룹

`application.yml`의 `springdoc.group-configs`에 `08-payment` 그룹을 추가했다
(`com.c203.limit.domain.payment.controller`). 태그는 `08. 결제`를 사용한다.
