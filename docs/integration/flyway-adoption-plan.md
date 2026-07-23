# Flyway 도입 및 도메인 스키마 통합 기록

- 최초 작성: 2026-07-22
- 최종 통합: 2026-07-23
- 현재 운영 절차: `database-schema-runbook.md`

이 문서는 Flyway 도입과 회원·인증·관리자 및 신규 도메인 스키마를 통합한 결정 기록이다. 실제 배포 시에는 반드시 Runbook을 최종 기준으로 사용한다.

## 적용 원칙

- 운영에서는 Hibernate 자동 DDL 변경을 금지하고 `ddl-auto=validate`만 사용한다.
- DB 변경은 Flyway의 새 버전 migration으로만 추가한다.
- 이미 적용된 migration 파일은 수정하지 않는다.
- PK는 `BIGINT AUTO_INCREMENT`와 `GenerationType.IDENTITY`를 사용한다.
- 상태·타입은 DB `VARCHAR`와 Java enum `EnumType.STRING`을 기본으로 한다.
- 도메인 간 FK는 강한 일관성이 필요한 관계에만 둔다.
- 운영 DB 적용, Flyway `baseline`·`repair`, 백업 복원은 담당자 승인 후 수행한다.

## 의존성과 설정

```gradle
implementation 'org.springframework.boot:spring-boot-starter-flyway'
runtimeOnly 'org.flywaydb:flyway-mysql'
```

```yaml
spring:
  flyway:
    enabled: true
    locations: classpath:db/migration
    baseline-on-migrate: ${FLYWAY_BASELINE_ON_MIGRATE:false}
    baseline-version: ${FLYWAY_BASELINE_VERSION:1}
    validate-migration-naming: true
    clean-disabled: true
```

운영 기본값은 baseline 비활성화다. Flyway 이력이 없는 기존 운영 DB를 처음 전환할 때만 `baseline-on-migrate=true`, `baseline-version=1`을 사용한다.

## 최종 마이그레이션 구조

```text
db/migration/
├─ V1__create_base_schema.sql
├─ V2__create_chat_rtc_call_tables.sql
├─ V3__create_inspection_tables.sql
├─ V4__create_payment_refund_settlement_tables.sql
├─ V20260721__member_admin_alignment.sql
└─ V20260722__member_terms_agreement.sql
```

- V1은 빈 DB의 회원·인증·관리자 기준 스키마다.
- V2~V4는 chat/rtc/call, inspection, payment/refund/settlement 도메인을 추가한다.
- V20260721과 V20260722는 기존 운영 DB도 자동 전환할 수 있도록 비파괴·반복 안전 방식으로 보정했다.
- 과거 `db/manual` SQL은 자동 적용 대상에서 제외했던 초안이다. 최종 통합에서는 삭제·데이터 유실 구문을 제거한 뒤 versioned migration으로 편입했다.
- Docker init SQL 마운트는 사용하지 않는다. 스키마 적용 주체를 Flyway 하나로 유지한다.

## 병합 중 확인한 사항

- `admin_account.updated_at`, `last_login_at`, `password_changed_at`은 migration과 JPA 매핑을 함께 활성화했다.
- `member_terms_agreement`는 migration, Entity, Repository, 가입 시 저장 로직을 함께 활성화했다.
- 기존 `member_role`, `user_sanction`, `user_account.member_type` 데이터는 삭제하지 않는다.
- 기존 `member_type=SELLER` 같은 값도 보존한다.
- 빈 DB와 레거시 DB를 각각 Testcontainers로 검증한다.

## 검증 기준

1. 빈 MySQL에서 V1부터 전체 migration이 성공한다.
2. Hibernate `ddl-auto=validate`가 전체 Entity를 검증한다.
3. V1 수준의 기존 DB를 baseline한 뒤 V2 이후 migration이 성공한다.
4. 회원·관리자 누락 컬럼과 신규 도메인 테이블이 존재한다.
5. 기존 회원 역할·제재 테이블과 `member_type` 데이터가 보존된다.
6. 단위 테스트, 통합 테스트, Spotless, `bootJar`가 통과한다.

초기 도입 MR과 당시 조사 내용은 Git 이력에서 확인할 수 있으며, 현재 배포 순서와 복구 방법은 `database-schema-runbook.md`를 따른다.
