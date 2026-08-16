# 결제 동시성 검증 2 — Idempotency 테스트

- 브랜치: `test/payment-concurrency-observability`
- 관련 코드: `PaymentService.request()`(:76-101), `recoverOrConflict()`(:103-114), `createPayment()`의 `existingRequested` 재사용 경로(:138-149), `continueRequestedPayment()`(:231-252)
- 선행 문서: [[payment-concurrency-ownership]] (같은 형식, 같은 [LOAD] 매물 규칙 사용)

## 1. 목적

같은 사용자가 동일 listing·method·idempotencyKey로 보낸 요청 20개가 동시에 들어와도 중복 Payment를 만들지 않고 전부 같은 Payment로 수렴하는지 검증한다.

## 2. 예측 결과 (실행 전 코드 추적 기반)

- 모든 응답 = 201, 성공 응답의 paymentId 집합 크기 = 1
- 근거: `listing.reserve()`와 `Payment` insert가 같은 트랜잭션에서 원자적으로 커밋되므로, 늦게 도착한 요청이 "listing은 RESERVED로 바뀐 걸 보는데 Payment는 못 보는" 창구가 생기지 않는다. 늦은 요청은 outer 루프의 `findByBuyerIdAndIdempotencyKey` 재조회(:80) 또는 `createPayment()`의 `existingRequested` 재조회(:138-140) 중 하나로 반드시 승자의 Payment를 찾아 수렴한다.
- **주의 — 이론적 예외**: 승자와 트랜잭션이 실제로 겹친 요청은 낙관적 락 충돌로 `MAX_CREATE_ATTEMPTS=3`회까지 재시도한다. 이 3회 안에 안정 상태를 못 잡으면 이론상 PAY005가 섞일 수 있다. **PAY005가 하나라도 나오면**: 데이터 무결성 자체는 깨지지 않았는지 DB로 확인하고, 버그가 아니라 재시도 파라미터(`MAX_CREATE_ATTEMPTS`) 튜닝 이슈로 분류한다.
- **PAY004가 하나라도 나오면 실패**: API 계약상 동일 요청은 기존 Payment로 수렴해야 하며, `IDEMPOTENCY_KEY_CONFLICT`가 나온다는 건 listing/method 불일치로 오인됐다는 뜻이라 로직 결함이다.
- 5xx = 0건

## 3. 사전 준비

1. 대상 [LOAD] 매물 1건 (idempotency 전용, ownership 테스트와 다른 매물 사용)
2. buyer 1명의 토큰만 필요 (ownership 테스트보다 준비가 단순함)
3. k6 스크립트: 동일 listing/method/idempotencyKey로 20개 요청을 `http.batch()`로 동시 전송

## 4. 진행 순서

1. Grafana absolute time range 시작점 고정
2. `up{job="spring-local"}` = 1 확인
3. DB에 idempotency 전용 [LOAD] 매물 상태 ON_SALE 확인
4. k6 스크립트 실행
5. k6 콘솔 summary 저장, 성공 응답의 paymentId들이 전부 동일한지 스크립트 자체 assertion으로도 확인
6. DB 검증 쿼리 실행 및 저장
7. 같은 시간 범위로 Grafana 캡처
8. 종료 후 1~2분 뒤 회복 확인

## 5. 로그를 어디서 어떻게 찍는가

Test 1과 동일한 규칙. 저장 위치만 구분:

| 항목 | 방법 | 저장 위치 |
|---|---|---|
| k6 콘솔 결과 | `k6 run --summary-export=summary.json script.js \| tee console.txt` | `docs/testing/results/idempotency/` |
| DB 검증 쿼리 | 리다이렉트 또는 스크린샷 | `docs/testing/results/idempotency/db-check.txt` |
| Grafana 캡처 | 스크린샷 | `docs/testing/results/idempotency/grafana-*.png` |

## 6. 실행 결과 (실행 후 채움)

| 항목 | 기대값 | 실제값 | 비고 |
|---|---|---|---|
| HTTP 201 | 20 | 20 | |
| 성공 응답 paymentId 집합 크기 | 1 | 1 | payment_id=17 |
| PAY004 발생 건수 | 0 | 0 | |
| PAY005 발생 건수 | 0 (이론상 가능) | 0 | |
| 5xx | 0 | 0 | |
| 해당 idempotency_key Payment 수 | 1 | 1 | |
| listing.status | RESERVED | RESERVED | buyer_id=2 |
| REQUESTED Payment 수 | 1 | 1 | |

## 7. 결론 (실행 후 채움)

- 합격/불합격: **합격**
- PAY005 발생 시 재분류 근거: 해당 없음 (0건)
- 특이사항: k6 종료 직후 바로 DB를 확인해 ownership 테스트에서 겪은 TTL 초과 문제 없이 한 번에 검증 완료.
- 다음 테스트로 넘어가도 되는지: 예
