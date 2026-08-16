# 결제 동시성 검증 5 — DB 검증 쿼리

- 대상: [[payment-concurrency-ownership]], [[payment-concurrency-idempotency]], [[payment-concurrency-key-misuse]] 공통으로 사용
- 컬럼명 확인됨: `listing.id`(PK), `payment.payment_id`(PK) — `Listing.java:43`, `Payment.java:32`와 대조 완료

## 1. 목적

각 테스트가 끝난 직후 애플리케이션 응답(HTTP 상태 코드)만으로는 알 수 없는 실제 데이터 불변식(정확히 몇 건이 생성됐는지, 어떤 상태로 남았는지)을 직접 확인한다.

## 2. 쿼리 목록

```sql
-- 대상 매물 상태 확인 (ownership, idempotency 공통)
SELECT id, title, status, buyer_id, reserved_until, version
FROM listing
WHERE title LIKE '[LOAD]%';

-- listing별 Payment 상태·건수
SELECT listing_id, status, COUNT(*) AS payment_count
FROM payment
WHERE listing_id IN (/* 대상 listing id */)
GROUP BY listing_id, status;

-- idempotency key별 Payment 매핑 (idempotency, key-misuse 테스트)
SELECT idempotency_key, payment_id, listing_id, buyer_id, status
FROM payment
WHERE idempotency_key LIKE 'load-%'
ORDER BY payment_id;
```

## 3. 언제 실행하는가

각 k6/curl 실행 직후, Grafana 캡처보다 먼저 실행한다 (DB 상태가 정합성의 1차 근거이고, Grafana는 보조 근거이기 때문). [[payment-concurrency-ownership]] 진행 순서 6번, [[payment-concurrency-idempotency]] 진행 순서 6번, [[payment-concurrency-key-misuse]] 진행 순서 5번과 대응.

## 4. 로그를 어디서 어떻게 찍는가

| 항목 | 방법 | 저장 위치 |
|---|---|---|
| 쿼리 실행 결과 | mysql client: `mysql ... -e "<query>" > result.txt` 또는 GUI 클라이언트 스크린샷 | 각 테스트별 `docs/testing/results/<test>/db-check.txt` |

**주의**: 쿼리 결과에 access token이나 실제 환경변수 값은 포함되지 않지만(스키마상 토큰 컬럼 없음), mysql 접속 명령 자체에 비밀번호가 노출되지 않도록 `-p` 플래그로 프롬프트 입력하거나 `MYSQL_PWD` 환경변수를 쓰고, 저장하는 텍스트에는 접속 명령 줄 자체를 포함시키지 않는다(쿼리 결과만 저장).

## 5. 실행 결과 (실행 후 채움 — 테스트별로 섹션 나눔)

### ownership
```
(여기에 쿼리 결과 붙여넣기)
```

### idempotency
```
(여기에 쿼리 결과 붙여넣기)
```

### key-misuse
```
(여기에 쿼리 결과 붙여넣기)
```

## 6. 결론 (실행 후 채움)

- 세 테스트 모두 DB 불변식을 만족했는지:
- 특이사항:
