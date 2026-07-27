# 결제 API 구현 기록

## 구현 범위

- 결제 요청 생성 `POST /api/v1/payments`
- 결제 상세 조회 `GET /api/v1/payments/{paymentId}`

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
`PAYMENT_REQUEST_CONFLICT`(`PAY001~005`)만 새로 추가했다.

## 남은 위험 · 확인 필요 사항

- 매물을 `ON_SALE`에서 `RESERVED`로 옮기는 별도의 "예약" 진입점이 아직 없다. 지금은 결제 요청
  생성이 예약과 결제를 한 번에 처리하는 유일한 경로다. 채팅 등에서 별도 예약 버튼이 추가되면
  이 가정을 다시 검토해야 한다.
- 예약 만료(`reserved_until` 경과) 배치/스케줄러는 범위 밖이라 만료된 예약에 대한 결제 요청을
  막는 로직이 없다.
- PG 연동, 웹훅 승인(`Payment.approve`), 결제 실패·재시도(`Payment.retry`)는 이 PR에서 다루지
  않았다.

## Swagger 그룹

`application.yml`의 `springdoc.group-configs`에 `07-payment` 그룹을 추가했다
(`com.c203.limit.domain.payment.controller`). 태그는 `07. 결제`를 사용한다.
