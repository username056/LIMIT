# Flyway DB 스키마 운영 Runbook

MySQL 스키마는 애플리케이션 시작 시 Flyway가 적용하고, Hibernate `ddl-auto=validate`가 모든 Entity와 최종 스키마의 정합성을 검증한다. MySQL DDL은 완전한 트랜잭션 롤백을 보장하지 않으므로 운영 전환 전 복구 가능한 백업이 필수다.

## 마이그레이션 구성

- `V1__create_base_schema.sql`: 빈 DB의 회원·인증·관리자 기준 스키마
- `V2__create_chat_rtc_call_tables.sql`: 채팅·RTC·통화 예약 스키마
- `V3__create_inspection_tables.sql`: 검수 스키마
- `V4__create_payment_refund_settlement_tables.sql`: 결제·환불·정산 스키마
- `V20260721__member_admin_alignment.sql`: 기존 DB의 회원·관리자 누락 컬럼과 테이블을 비파괴 방식으로 보정
- `V20260722__member_terms_agreement.sql`: 회원 약관 동의 이력 테이블 보정
- `V20260723__create_product_checklist_tables.sql`: 상품·체크리스트·증거·재검수 스키마 추가
- `V20260802__create_instant_seller_profiles.sql`: 즉시 판매자 등록 프로필과 기존 판매자 데이터 호환 이관
- `V20260809__create_model_checklist_research.sql`: 모델별 AI 조사 상태·결과·승인 템플릿 연결 추가
- `V20260810__create_device_model_request.sql`: 판매자의 미등록 기기 모델 검토 요청 추가

`V20260721`은 기존 `member_role`, `user_sanction`, `user_account.member_type`을 삭제하지 않는다. 기존 `member_type` 값도 보존하며, 신규 회원 INSERT를 막지 않도록 해당 컬럼만 nullable/default 호환 상태로 바꾼다.

이미 적용된 migration 파일은 수정하지 않는다. 추가 변경은 반드시 더 높은 버전의 새 파일로 작성한다.

## 빈 DB 적용

빈 DB에서는 `FLYWAY_BASELINE_ON_MIGRATE=false` 상태로 시작한다. Flyway가 V1부터 모든 migration을 순서대로 실행한 뒤 Hibernate validate와 readiness가 통과해야 한다.

Docker MySQL의 `/docker-entrypoint-initdb.d`에는 Flyway SQL을 별도로 마운트하지 않는다. 동일 SQL을 Docker와 Flyway가 중복 실행하면 이력과 실제 스키마가 어긋날 수 있다.

## 기존 운영 DB 최초 전환

1. 배포 시각을 확정하고 복구 가능한 MySQL 백업을 만든다.
2. `flyway_schema_history`가 없는지 확인하고 `user_account`, `admin_account`의 컬럼과 주요 데이터 건수를 기록한다.
3. 운영 환경 파일에 아래 값을 한 번만 설정한다.

```env
FLYWAY_BASELINE_ON_MIGRATE=true
FLYWAY_BASELINE_VERSION=1
```

4. 신규 Blue 컨테이너를 기동한다. 기존 기본 스키마는 V1로 baseline되고, V2 이후 migration이 순서대로 적용된다.
5. `flyway_schema_history`에서 baseline V1부터 최신 migration까지 성공 상태를 확인한다.
6. Hibernate validate와 readiness가 통과했는지 확인한다.
7. 회원가입, 이메일·소셜 로그인, 판매자 즉시 등록·토큰 갱신·상품 등록 차단, 관리자 로그인과 회원 목록 조회를 smoke test한다.
8. 문제가 없을 때만 트래픽을 전환한다.
9. 운영 환경 파일을 `FLYWAY_BASELINE_ON_MIGRATE=false`로 되돌리고 다음 배포 기동을 확인한다.

`baseline-on-migrate`는 잘못된 DB를 정상 기준으로 오인할 가능성이 있으므로 최초 전환 외에는 활성화하지 않는다. 운영 DB 적용과 Flyway `baseline`·`repair` 수동 실행은 담당자 승인 없이 진행하지 않는다.

## 기존 로컬 DB

로컬 Compose는 기존 볼륨을 자동 수용하기 위해 기본적으로 baseline을 활성화한다.

```powershell
docker compose --env-file infra/.env.local -p limit-local -f infra/compose.yml -f infra/compose.local.yml up -d --build backend
```

새 로컬 DB에서 전체 이력을 V1부터 확인하려면 `infra/.env.local`에 `FLYWAY_BASELINE_ON_MIGRATE=false`를 설정하고 새 볼륨으로 시작한다. 기존 볼륨을 삭제하는 작업은 데이터가 사라지므로 필요한 데이터가 없는지 먼저 확인한다.

## 실패와 복구

- Flyway 또는 Hibernate validate가 실패한 Blue는 트래픽에 연결하지 않고 Green을 유지한다.
- `flyway_schema_history`의 실패 행을 임의 삭제하거나 원인 확인 없이 `repair`하지 않는다.
- MySQL DDL이 일부 반영됐을 수 있으므로 애플리케이션 로그와 실제 스키마를 함께 대조한다.
- 원인과 영향 범위를 확정한 뒤 백업 복원 또는 새 후속 migration 중 하나를 선택한다.
- 실제 `.env`, Secret, 토큰, 키는 저장소에 커밋하지 않는다.

### 판매자 migration 읽기 전용 진단

`V20260802` 실패를 조사할 때는 `seller_migration_diagnostic` Job으로
`flyway_schema_history`, `seller` 컬럼·인덱스와 중복·길이 집계만 확인한다. 이 Job은
제목에 `판매자 마이그레이션 읽기 전용 진단`이 포함된 `dev` push에서만 실행하며,
같은 파이프라인의 이미지 push와 백엔드·모니터링·프론트 운영 배포는 건너뛴다.
회원 ID나 이메일, 이름, 연락처, 계좌 데이터는 출력하지 않는다.

진단 결과만으로 실패 행을 삭제하거나 `repair`하지 않는다. 실제 스키마 보정,
Flyway `repair`와 배포 재시도는 별도 운영 승인을 받은 뒤 수행한다.

### V20260802 판매자 migration 승인 복구

진단 결과가 아래 조건과 모두 일치하고 운영 변경 승인을 받은 경우에만
`seller_migration_repair_deploy` Job을 사용한다.

- `V20260802` 실패 이력 1건, 성공 이력 0건
- `seller` 행 0건과 `user_id` 중복 0건
- `member_type=SELLER` 회원 1명과 판매자 프로필 누락 1건
- `approved_at`, `product_limit`, `sales_amount_limit`이 `NOT NULL`이며 기본값 없음

Job은 변경 전에 `backup-datastores.sh`를 성공시킨 뒤
`db/maintenance/V20260802__prepare_failed_seller_migration.sql`로 행 데이터 변경 없이
기존 컬럼의 nullable/default만 보정한다. 프로젝트와 동일한
`flyway/flyway:11.14.1-alpine` 이미지로 `repair`하고, 검증·스캔된 backend image를
Blue-Green 배포한다. `repair`에는 애플리케이션과 같은 `db/migration` 위치를 전달한다.

Job은 제목에 `판매자 마이그레이션 복구와 재배포`가 포함된 `dev` push에서만 실행한다.
같은 파이프라인의 일반 `deploy_prod`, 모니터링 배포와 프론트 배포는 건너뛴다.
복구 완료 조건은 다음과 같다.

- `V20260802 success=1`, 실패 이력 0건
- 판매자 프로필 1건, SELLER 프로필 누락 0건, `user_id` 중복 0건
- 기존 한도 컬럼 기본값 적용과 `approved_at` nullable 상태
- 신규 backend readiness 및 외부 `/health` smoke 통과

사전 조건이 하나라도 달라졌거나 백업·스키마 보정·repair·배포 후 검증 중 하나라도
실패하면 Job은 즉시 중단한다. 실패한 신규 색상은 트래픽에 연결하지 않으며,
DB 백업 복원은 원인과 영향 범위를 다시 확인하고 별도 승인 후 수행한다.
