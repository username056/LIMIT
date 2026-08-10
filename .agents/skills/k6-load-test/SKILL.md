---
name: k6-load-test
description: k6로 API 또는 사용자 흐름의 smoke·load·stress·spike 테스트를 설계하거나 실행하고 재현 가능한 Baseline 및 Before/After 결과를 만들 때 사용한다. VU·arrival rate·stage·threshold·테스트 데이터·인증·결과 지표를 정의하는 성능 테스트 작업에 적용한다.
---

# k6 부하 테스트

## 준비

1. 루트 규칙과 도구별 `harness/performance.md`를 읽는다.
2. 승인된 비운영 대상 URL, 테스트 계정·데이터, 기대 트래픽 모델과 성공 기준을 확인한다.
3. 인증 정보와 URL은 환경변수로 받고 실제 값은 코드·로그·결과에 저장하지 않는다.

## 시나리오 설계

- 단일 endpoint 나열보다 로그인부터 핵심 행동까지 실제 사용자 흐름을 우선한다.
- smoke는 스크립트와 기능 확인, load는 기대 부하 검증, stress는 포화 지점 탐색, spike는 급증과 회복 확인에 사용한다.
- 동시 사용자 모델은 VU executor, 일정 요청률 모델은 arrival-rate executor를 사용한다. 근거 없이 둘을 대체하지 않는다.
- 성공 status뿐 아니라 응답 계약과 업무 성공 조건을 `check`로 검증한다.
- Average, p95, p99, RPS, Error Rate를 수집한다. endpoint·scenario별 tag를 사용해 느린 흐름을 구분한다.
- threshold는 요구사항 또는 Baseline 근거로 정한다. 임의의 500ms 같은 값을 팀 기준인 것처럼 만들지 않는다.
- 요청 사이의 think time, 데이터 선택 방식, setup과 teardown을 명시한다. 동일 레코드 경합이나 cache hit만 반복해 결과를 왜곡하지 않는다.

## 재현성 기록

실행마다 애플리케이션 commit, 환경, 인프라 자원, 데이터셋 크기·분포, cache 상태, k6 버전, 스크립트 hash, executor, VU 또는 arrival rate, stages, duration, warm-up, 시작·종료 시각을 기록한다. 원본 summary를 보존하고 반복 실행 결과를 개별로 남긴다.

Before와 After는 같은 스크립트와 조건을 사용한다. 필요한 조건 변경이 생기면 별도 실험으로 취급하고 직접 개선율을 계산하지 않는다.

## 실행 안전

- 운영 환경에는 명시적 승인 없이 실행하지 않는다. 대상이 불명확하면 실행하지 않고 스크립트와 명령 예시까지만 제공한다.
- stress·spike는 격리된 환경에서만 수행하고 중단 조건을 정한다.
- 기능 오류, 429·5xx, timeout 증가가 중단 조건을 넘으면 부하를 계속 올리지 않는다.
- 부하 발생기 자체의 CPU·network 포화도 확인해 서버 병목으로 오인하지 않는다.

## 결과 연결

실행 시간대를 기록해 Prometheus·Grafana와 SQL 근거를 같은 구간으로 조회한다. threshold 통과만으로 병목 해소를 단정하지 않고 실행 간 변동성과 서버 자원 포화를 함께 보고한다.
