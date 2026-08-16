# 결제 동시성 검증 1 — Listing ownership 테스트

- 브랜치: `test/payment-concurrency-observability`
- 관련 코드: `PaymentService.request()` / `createPayment()` (`backend/src/main/java/com/c203/limit/domain/payment/service/PaymentService.java:76-175`), `ErrorCode.LISTING_NOT_ON_SALE`(PRD001)·`ErrorCode.PAYMENT_REQUEST_CONFLICT`(PAY005)

## 1. 목적

한 매물(listing)에 대해 서로 다른 구매자 20명이 동시에 결제를 요청했을 때, 정확히 1명만 RESERVED·REQUESTED Payment를 얻는지 검증한다. 동시성 하에서 이중 예약(overselling)이 발생하지 않는지가 핵심이다.

## 2. 예측 결과 (실행 전 코드 추적 기반)

- HTTP 201 = 1건 (가장 먼저 `listing.reserve()` + `Payment` insert를 커밋한 요청)
- HTTP 409 = 19건
  - 그중 일부는 **PRD001**: 뒤늦게 읽은 요청이 이미 RESERVED로 바뀐 listing.status를 보고 `requireStatus(ON_SALE)`에서 즉시 실패 (재시도 없음)
  - 그중 일부는 **PAY005**: 승자와 트랜잭션이 겹쳐 낙관적 락(`@Version`) 충돌 → `MAX_CREATE_ATTEMPTS=3`회 재시도 후에도 소진된 경우
  - 어느 쪽이 몇 건씩 나올지는 타이밍에 좌우되므로 비율은 예측하지 않음 (합이 19건이고 이 두 코드만 나오는지가 판정 기준)
- 5xx = 0건
- DB: 대상 listing.status=RESERVED, buyer_id=201 응답의 buyer, REQUESTED Payment 1건, listing 전체 Payment 1건

## 3. 사전 준비

1. 대상 [LOAD] 매물 1건 준비 (title `[LOAD]%`, status=ON_SALE, seller는 buyer 20명과 겹치지 않는 계정)
2. buyer 계정 20개 준비. 실제 로그인 토큰은 파일에 하드코딩하지 말고 k6 `setup()` 단계에서 로그인 API를 호출해 매 실행마다 발급받는다.
   - 자격증명(테스트 계정 id/pw)은 `__ENV.LOAD_TEST_BUYER_*` 같은 환경변수로만 참조한다.
   - **스크립트·주석 어디에도 실제 `.env` 계열 파일 경로 문자열(`infra/.env.local` 등)을 적지 않는다** — 이 값만으로 로컬 시크릿 보호 훅이 차단하는 걸 확인함. 대신 값의 존재만 언급하거나 `README`에 별도로 안내.
3. k6 스크립트: `http.batch()`로 20개 POST `/api/v1/payments`를 동시 전송하도록 작성 (서로 다른 buyer 토큰 × 서로 다른 idempotencyKey)

## 4. 진행 순서

1. Grafana Explore에서 absolute time range 시작점을 고정하기 직전 시각 기록
2. `up{job="spring-local"}` = 1 확인 (스크린샷)
3. DB에 [LOAD] 매물 상태가 ON_SALE인지 사전 확인
4. k6 스크립트 실행
5. k6 콘솔 summary 저장
6. 종료 직후 DB 검증 쿼리 3종 실행 및 결과 저장
7. 같은 시간 범위로 Grafana에서 RPS/5xx/Hikari 캡처
8. 종료 후 1~2분 뒤 Hikari pending=0 등 회복 확인 캡처

## 5. 로그를 어디서 어떻게 찍는가

| 항목 | 방법 | 저장 위치(제안) |
|---|---|---|
| k6 콘솔 결과 | `k6 run --summary-export=summary.json script.js \| tee console.txt` | `docs/testing/results/ownership/console.txt`, `summary.json` |
| DB 검증 쿼리 | mysql client로 실행 후 `> result.txt` 리다이렉트 (혹은 GUI 캡처 스크린샷) | `docs/testing/results/ownership/db-check.txt` |
| Grafana 캡처 | Explore 화면 스크린샷 (OS 스크린샷 도구) | `docs/testing/results/ownership/grafana-*.png` |
| 판정 요약 | 아래 6번 표를 직접 채움 | 본 파일 |

`docs/testing/results/` 폴더는 아직 없으므로 처음 캡처 저장할 때 함께 생성하면 된다. 텍스트/스크린샷에 access token, 실제 비밀번호, `.env` 값이 들어가지 않았는지 저장 전에 한 번 확인.

## 6. 실행 결과 (실행 후 채움)

| 항목 | 기대값 | 실제값 | 비고 |
|---|---|---|---|
| HTTP 201 | 1 | 1 | |
| HTTP 409 | 19 | 19 | |
| 409 중 PRD001 | - | 19 | |
| 409 중 PAY005 | - | 0 | |
| 409 중 그 외 코드 | 0 | 0 | |
| 5xx | 0 | 0 | |
| listing.status | RESERVED | RESERVED | |
| listing.buyer_id | 201 응답 buyer와 일치 | buyer_id=4 (일치) | |
| REQUESTED Payment 수 | 1 | 1 | |
| listing 전체 Payment 수 | 1 | 1 | 아래 특이사항 참고 |
| Hikari pending (종료+2분) | 0 | 0 | active=0, max=10도 확인 |

## 7. 결론 (실행 후 채움)

- 합격/불합격: **합격**
- 특이사항: 1차 실행은 k6 종료 후 DB 확인까지 시간이 지체되어 `reserved_until`(기본 TTL 10분, `limit.product.reservation-ttl-minutes`)을 초과, `PaymentReservationExpirationScheduler`가 listing을 자동으로 ON_SALE/EXPIRED로 되돌렸다. k6 판정 자체는 1차·2차 모두 4개 체크 전부 통과했으나, DB 스냅샷은 TTL 안에 찍어야 RESERVED/REQUESTED를 확인할 수 있다는 재현 조건을 확인. 2차 실행을 즉시 DB 검증까지 마쳤고, 위 표는 2차 실행 기준. 1차 실행분의 EXPIRED Payment 잔여 행은 검증 후 삭제.
- 다음 테스트로 넘어가도 되는지: 예
