# 결제 동시성 k6 스크립트

`docs/testing/payment-concurrency-*.md` 5개 문서와 세트로 사용한다. 스크립트 자체가 아니라 각 문서가 목적·예측 결과·판정 기준의 근거다.

## 사전 준비

1. 로컬 스택 기동 확인: `docker ps`에 `limit-local-backend-1`, `limit-local-mysql-1`, `limit-local-prometheus-1`, `limit-local-grafana-1` 모두 `Up (healthy)`
2. 백엔드는 호스트에 `18080`으로 매핑되어 있다(`docker port limit-local-backend-1`로 재확인 가능). MySQL은 호스트에 포트가 열려있지 않아 컨테이너 안에서 실행해야 한다.
3. 테스트 데이터 시딩 (MySQL 비밀번호는 셸 환경변수 `MYSQL_PWD`로 미리 export해두고, 파일에는 적지 않는다):
   ```
   docker exec -e MYSQL_PWD -i limit-local-mysql-1 mysql -u limit limit \
     < scripts/local/seed-load-test-payment.sql
   ```
4. 생성된 listing id 조회 후 아래 환경변수에 매핑:
   ```
   docker exec -e MYSQL_PWD -i limit-local-mysql-1 mysql -u limit limit \
     -e "SELECT id, title FROM listing WHERE title LIKE '[LOAD]%';"
   ```

## 환경변수

실제 값은 커밋하지 않는다. 실행할 때만 셸에서 주입한다.

| 변수 | 용도 |
|---|---|
| `BASE_URL` | 기본 `http://localhost:18080` (호스트에 매핑된 실제 포트) |
| `LOAD_TEST_PASSWORD` | `scripts/local/seed-load-test-payment.sql` 주석에 적힌 로컬 전용 로그인 비밀번호 |
| `LOAD_TEST_OWNERSHIP_LISTING_ID` | `[LOAD] Ownership Test Listing`의 id |
| `LOAD_TEST_IDEMPOTENCY_LISTING_ID` | `[LOAD] Idempotency Test Listing`의 id |
| `LOAD_TEST_MISUSE_LISTING_ID_A` | `[LOAD] Key Misuse Test Listing A`의 id |
| `LOAD_TEST_MISUSE_LISTING_ID_B` | `[LOAD] Key Misuse Test Listing B`의 id |

## 실행

```
k6 run \
  --summary-export=summary.json \
  -e BASE_URL=http://localhost:18080 \
  -e LOAD_TEST_PASSWORD=<위 안내 참고> \
  -e LOAD_TEST_OWNERSHIP_LISTING_ID=<id> \
  performance/k6/ownership.js | tee console.txt
```

`idempotency.js`, `key-misuse.js`도 각각 필요한 env var만 바꿔 같은 방식으로 실행한다.

콘솔·summary 저장 위치는 `docs/testing/results/<test-name>/`를 따른다 (각 테스트 문서의 "로그를 어디서 어떻게 찍는가" 절 참고).

## 이 폴더에서 지키는 것

- 자격증명·URL은 `__ENV`로만 받는다 (`lib/auth.js`).
- 실제 로컬 환경 파일 경로 문자열(예: `infra/.env.local`)을 스크립트나 주석에 적지 않는다 — 값을 노출하지 않아도 경로 문자열만으로 이 저장소의 secret 보호 훅이 파일 쓰기를 차단한다.
- 운영 URL을 대상으로 실행하지 않는다. `BASE_URL`이 로컬이 아니면 실행하지 않는다.
