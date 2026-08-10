---
name: observability-analysis
description: k6 부하 구간을 Prometheus·Grafana의 Spring Boot, JVM, HikariCP, 호스트, 컨테이너, MySQL·MongoDB·Redis·Nginx 지표와 연결해 병목과 포화 원인을 분석할 때 사용한다. 대시보드·PromQL 근거 수집, Before/After 서버 지표 비교, 회복 여부 확인에 적용한다.
---

# Observability 분석

## 프로젝트 근거

- Spring Actuator Prometheus endpoint: `backend/src/main/resources/application.yml`
- Prometheus scrape·alert: `infra/monitoring/prometheus/`
- Grafana datasource·dashboard: `infra/monitoring/grafana/`
- 로컬 관측 스택: `infra/compose.local.yml`

설정과 대시보드의 실제 metric 이름을 먼저 확인하고 기억에 의존한 PromQL을 만들지 않는다.

## 분석 순서

1. 루트 규칙과 도구별 `harness/performance.md`를 읽는다.
2. k6 시작·종료와 warm-up 시간을 기준으로 같은 시간 범위를 고정한다.
3. 요청률·오류·latency 변화와 애플리케이션·인프라 지표의 변곡점을 나란히 본다.
4. 정상 구간, 성능 저하 구간, 부하 종료 후 회복 구간을 비교한다.
5. 한 지표만으로 인과를 단정하지 않고 요청 지표와 saturation 또는 latency 근거를 함께 제시한다.

## 우선 확인 지표

- HTTP: request rate, latency 분포, 4xx·5xx, active request
- JVM: process CPU, heap·non-heap, allocation, GC pause·frequency, thread와 deadlock
- DB pool: HikariCP active, idle, pending, max, acquire timeout
- Database: connection, query latency, lock·row activity, slow query 근거
- Host·container: CPU utilization·throttling, memory·OOM, disk I/O, network
- Proxy·cache: Nginx status·latency, Redis connection·hit/miss·latency

평균값이 peak를 숨기지 않도록 rate 구간과 percentile을 명시한다. 패널 screenshot만 남기지 말고 datasource, query 또는 panel 이름, 시간 범위와 집계 단위를 기록한다.

## 판단 예시

- CPU가 낮다는 이유만으로 여유 있다고 단정하지 않는다. pending connection, lock, I/O, 외부 호출을 확인한다.
- Hikari active가 max에 붙고 pending과 API latency가 함께 증가할 때 pool 포화 가설을 강화한다. pool 크기 증가는 DB 수용량을 확인한 뒤 후보로만 비교한다.
- GC pause와 tail latency가 같은 구간에 반복될 때만 관련 가설을 세우고 allocation·heap 추세를 추가 확인한다.
- 부하 종료 후 지표가 기준선으로 돌아오지 않으면 회복 실패 또는 누수 가설로 기록한다.

## 보고

관측 시간대, 환경·버전, Grafana panel 또는 PromQL 근거, k6 지표와의 시간 상관, 확인·기각한 가설, 데이터 공백과 남은 위험을 Before/After로 정리한다. 상관관계는 추가 근거 없이 인과관계로 표현하지 않는다.
