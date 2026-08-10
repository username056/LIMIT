---
name: sql-query-analysis
description: Spring Data JPA·SQL 성능 가설을 요청별 query count, SQL 로그, EXPLAIN·실행계획과 실측 시간으로 검증할 때 사용한다. N+1, full scan, join·fetch 전략, projection, pagination, index, lock·transaction 병목 분석과 개선안 비교에 적용한다.
---

# SQL·쿼리 분석

## 분석 순서

1. 루트·백엔드 규칙과 도구별 `harness/performance.md`를 읽는다.
2. API에서 Repository까지 호출 경로, transaction 경계, association 접근과 반복문을 추적해 가설을 세운다.
3. 대표 데이터와 부하에서 요청당 query count, 중복 SQL, query latency를 측정한다. 코드만 보고 N+1 또는 full scan으로 확정하지 않는다.
4. 의심 SQL과 bind 값의 형태를 확보하되 개인정보와 Secret은 기록하지 않는다.
5. 대상 DB와 유사한 비운영 환경에서 `EXPLAIN` 또는 실행계획을 확인한다. 실행을 수반하는 분석 명령은 읽기 쿼리와 안전한 환경에서만 사용한다.
6. 예상 rows와 실제 데이터 규모, access type, 선택된 index, join 순서, sort·temporary, filtered 비율을 함께 해석한다.
7. 최소 두 개선안을 비교한 후 가장 작은 변경을 선택한다.

## 후보별 확인점

- fetch join: query 감소와 pagination·row multiplication 영향을 함께 확인한다.
- batch fetch: 추가 query 수, batch 크기와 메모리 사용을 확인한다.
- projection·query 재작성: 필요한 column만 조회하는지와 계약 변경 여부를 확인한다.
- index: where·join·order by 패턴, 선택도, composite column 순서, write·storage 비용을 확인한다.
- pagination: offset 증가 비용을 측정하고 keyset 전환 시 정렬 안정성과 API 계약을 확인한다.
- cache: 반복성과 hit ratio, invalidation·정합성·장애 복잡성을 먼저 확인한다.
- transaction·lock: 보유 시간, 대기 시간, deadlock 근거와 외부 호출 포함 여부를 확인한다.

## 검증

변경 전후에 동일 데이터와 요청으로 query count, 실행계획, query latency와 k6 지표를 다시 측정한다. SQL 수가 줄어도 응답시간이나 자원 사용이 개선되지 않으면 성공으로 단정하지 않는다.

인덱스나 스키마 migration은 제안과 근거까지만 작성하고 사전 승인 없이 생성·적용하지 않는다. 운영 DB에서 ad-hoc 분석이나 부하를 실행하지 않는다.

## 보고

가설, 측정 SQL·요청, 실행계획 핵심 근거, 후보 비교, 선택 이유, Before/After, 기능·정합성 검증과 남은 위험을 기록한다.
