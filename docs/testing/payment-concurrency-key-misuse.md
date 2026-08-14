# 결제 동시성 검증 3 — 멱등키 오용 계약 테스트

- 브랜치: `test/payment-concurrency-observability`
- 관련 코드: `PaymentService.toResponseOrConflict()`(:195-201) — listing_id 또는 method 불일치 시 `IDEMPOTENCY_KEY_CONFLICT`(PAY004)
- 앞의 두 테스트와 달리 **동시성 테스트가 아니라 순차 계약 테스트**

## 1. 목적

같은 idempotencyKey를 다른 listing 또는 다른 method로 재사용했을 때 API가 이를 "같은 요청"으로 잘못 수렴시키지 않고 명확히 거부하는지 검증한다.

## 2. 예측 결과 (실행 전 코드 추적 기반)

코드상 `toResponseOrConflict()`가 `payment.getListingId()`와 `request.getListingId()`, `payment.getMethod()`와 `request.getMethod()`를 각각 비교해 하나라도 다르면 즉시 PAY004를 던진다. 조건 분기가 아니라 순차 실행이므로 타이밍에 좌우되지 않고 결정적으로 아래 결과가 나와야 한다.

| 순서 | 요청 | 기대 응답 |
|---|---|---|
| 1 | buyer1, listing A, CARD, key K | 201 |
| 2 | buyer1, listing B, CARD, key K (listing만 다름) | 409 PAY004 |
| 3 | buyer1, listing A, ACCOUNT_TRANSFER, key K (method만 다름) | 409 PAY004 |

DB에는 최초 Payment(1번 요청) 하나만 남아야 한다.

## 3. 사전 준비

1. [LOAD] 매물 2건 (listing A, listing B), 둘 다 ON_SALE
2. buyer 1명의 토큰

## 4. 진행 순서

1. Grafana time range는 이 테스트만 따로 고정할 필요는 없음 (순차 실행이라 부하 성격 아님) — 다만 같은 세션에서 실행했다면 ownership/idempotency와 이어서 기록
2. 요청 1 실행 → 응답 저장
3. 요청 2 실행 → 응답 저장
4. 요청 3 실행 → 응답 저장
5. DB 검증 쿼리 실행

## 5. 로그를 어디서 어떻게 찍는가

순차 테스트라 k6 없이 `curl` 3회로도 충분하다.

| 항목 | 방법 | 저장 위치 |
|---|---|---|
| 요청/응답 3건 | `curl -i ... | tee -a console.txt` (요청마다 append) | `docs/testing/results/key-misuse/console.txt` |
| DB 검증 쿼리 | 리다이렉트 또는 스크린샷 | `docs/testing/results/key-misuse/db-check.txt` |

curl 명령에 실제 토큰 값이 찍히므로, 저장 전 토큰 문자열은 `<REDACTED>`로 치환할 것.

## 6. 실행 결과 (실행 후 채움)

| 요청 | 기대 | 실제 | 비고 |
|---|---|---|---|
| 1 (listing A, CARD) | 201 | | |
| 2 (listing B, CARD, 같은 key) | 409 PAY004 | | |
| 3 (listing A, ACCOUNT_TRANSFER, 같은 key) | 409 PAY004 | | |
| DB Payment 수 (key=K) | 1 | | |

## 7. 결론 (실행 후 채움)

- 합격/불합격:
- 특이사항:
