# 결제 동시성 검증 4 — Grafana 캡처 구성

- 대상: [[payment-concurrency-ownership]], [[payment-concurrency-idempotency]] 두 부하 테스트의 보조 근거
- 성격: 이 캡처만으로 정합성을 증명하지 않는다. 정합성의 증거는 DB 검증([[payment-concurrency-db-verification]])이고, Grafana는 "5xx·풀 이상 징후가 없었는지"를 보는 보조 자료다. 단발 테스트라 scrape interval 15초에 변화가 묻힐 수 있음을 감안한다.

## 1. 목적

k6 실행 구간 전후로 결제 API의 요청률·5xx·HikariCP 풀 상태·JVM heap을 같은 시간대로 고정해 확인하고, 이상 징후(5xx 발생, pending 연결 잔존, 풀 고갈)가 없었는지 기록한다.

## 2. 진행 순서

1. `http://localhost:3000` → Explore → Prometheus 데이터소스 선택
2. 테스트 시작 전부터 종료 후 2분까지 absolute time range 고정 (relative time 금지 — 스크린샷 재현성 때문)
3. 아래 PromQL을 순서대로 조회하며 캡처

## 3. PromQL 목록

```promql
# 수집 여부
up{job="spring-local"}

# 결제 생성 API 요청률
sum(rate(http_server_requests_seconds_count{job="spring-local", uri="/api/v1/payments"}[1m]))

# 결제 생성 API 5xx
sum(rate(http_server_requests_seconds_count{job="spring-local", uri="/api/v1/payments", status=~"5.."}[1m]))

# HikariCP 사용/대기/최대
hikaricp_connections_active{job="spring-local"}
hikaricp_connections_pending{job="spring-local"}
hikaricp_connections_max{job="spring-local"}

# JVM heap
sum by (area) (jvm_memory_used_bytes{job="spring-local", area="heap"})
```

서버 HTTP histogram bucket이 노출되지 않으므로 (`application.yml`에 `percentiles-histogram` 미설정 확인됨) Grafana에서 서버 p95/p99 계산은 하지 않는다. 지연시간 p95/p99는 k6 결과값을 쓴다.

## 4. 캡처 순서 (권장)

1. up=1 화면
2. ownership 실행 직후 k6 콘솔 summary (텍스트, Grafana 아님)
3. ownership DB 검증 결과 (텍스트, Grafana 아님)
4. 같은 시간 범위의 Grafana: 결제 RPS / 5xx / Hikari
5. idempotency도 같은 세트 (2~4 반복)
6. 종료 후 1~2분 회복 화면: pending이 0인지 확인

## 5. 로그를 어디서 어떻게 찍는가

| 항목 | 방법 | 저장 위치 |
|---|---|---|
| 각 패널 스크린샷 | OS 스크린샷 도구, absolute time range가 화면에 보이게 캡처 | `docs/testing/results/grafana/<step>-<panel>.png` |

파일명 규칙: `01-up.png`, `02-ownership-console.txt`(텍스트는 grafana 폴더 대신 각 테스트 폴더에), `03-ownership-db.txt`, `04-ownership-rps.png`, `04-ownership-5xx.png`, `04-ownership-hikari.png`, `05-idempotency-*`, `06-recovery-hikari.png` 형태로 스텝 번호를 접두어로 붙이면 순서가 파일 정렬만으로도 보인다.

## 6. 실행 결과 (실행 후 채움)

| 확인 항목 | 기대 | 실제 | 비고 |
|---|---|---|---|
| up | 1 | | |
| 결제 API 5xx (구간 내) | 0 | | |
| Hikari pending (부하 중 peak) | max(10) 미만에서 안정 | | |
| Hikari active (부하 중 peak) | | | |
| Hikari pending (종료+2분) | 0 | | |
| JVM heap 이상 증가 | 없음 | | |

## 7. 결론 (실행 후 채움)

- 5xx·풀 고갈·회복 실패 징후 유무:
- 특이사항:
