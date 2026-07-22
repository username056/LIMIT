# Flyway 도입 및 DB 컨벤션 적용 계획

- 작성일: 2026-07-22
- 배경: Notion `리드미.md > 05. DB` 팀 합의안(상태 컬럼 `VARCHAR + JPA EnumType.STRING`, PK `BIGINT AUTO_INCREMENT + GenerationType.IDENTITY`)을 실제 Flyway 마이그레이션 브랜치로 통합하기 위한 실행 계획. 각자 엔티티를 작성한 브랜치를 취합해 dev에 일괄 정리하는 작업의 사전 조사 결과와 절차를 담는다.
- 근거 문서: 루트 `AGENTS.md`, `backend/AGENTS.md`, `docs/integration/database-schema-runbook.md`, Notion `05. DB`

## 1. 현재 상태 진단

### 1-1. 이미 준수되고 있는 것

레포 전체(로컬 브랜치 + 원격 `feat/*` 브랜치)를 대상으로 엔티티와 DDL을 grep한 결과:

- **PK 생성 전략은 이미 100% 통일되어 있음.** 조사한 모든 엔티티(`Member`, `AdminAccount`, `AdminActionLog`, `MemberRestriction`, `SocialAccount`, `Seller`, `SellerApplication`, `Inquiry`, `WithdrawalRequest` 등)가 `@Id @GeneratedValue(strategy = GenerationType.IDENTITY)`를 사용하고, 대응하는 DDL도 전부 `BIGINT NOT NULL AUTO_INCREMENT`로 작성되어 있다. 이 항목은 팀원들에게 별도로 수정 요청할 필요가 없다.
- **DDL 레벨의 상태 컬럼도 이미 `VARCHAR`로 작성되어 있음.** `member-admin-schema.sql`, `V20260721__member_admin_alignment.sql` 등 어디에도 MySQL 네이티브 `ENUM(...)` 타입은 쓰이지 않았다.

### 1-2. 갭이 있는 것 — JPA Enum 매핑 미적용

DDL은 VARCHAR인데, 일부 엔티티는 그 컬럼을 실제 Java `enum` 없이 **순수 `String` 필드**로 매핑하고 있다. `EnumType.STRING` 합의는 "DB 컬럼 타입"뿐 아니라 "코드가 SSOT가 되도록 Java enum + `@Enumerated(STRING)`을 쓴다"는 것이 핵심이므로, 아래 항목은 미준수로 분류한다.

| 브랜치 | 엔티티 | 미준수 필드 | 비고 |
|---|---|---|---|
| `feat/member-auth-admin-migration`, `feat/member-seller-admin-hardening`, `feat/frontend-scaffold` | `AdminAccount` | `role`, `status` (String) | enum 클래스 없음 |
| 위와 동일 | `AdminActionLog` | `actionType`, `targetType` (String) | enum 클래스 없음 |
| 위와 동일 | `MemberRestriction` | `type`, `status` (String) | enum 클래스 없음 |
| `feat/member-seller-admin-hardening`, `feat/frontend-scaffold` | `Inquiry` | `category`, `status` (String) | 상태 전이 로직이 문자열 비교로 구현됨 |
| 위와 동일 | `InquiryAnswer` | 상태 표현 없음(`isCurrent` boolean만 존재) | — |
| 위와 동일 | `WithdrawalRequest` | `status` (String) | enum 클래스 없음 |

반대로 `SocialAccount.provider`(`SocialProvider`), `Member.status`/`Member.role`(`MemberStatus`/`MemberRole`), `MemberTermsAgreement`(`TermsCode`), `Seller`/`SellerApplication`(`SellerType`, `SellerApplicationStatus`)는 이미 enum + `@Enumerated(EnumType.STRING)`으로 올바르게 되어 있어 그대로 가져가면 된다.

### 1-3. 갭이 있는 것 — Flyway 미도입

`backend/build.gradle`에 `flyway-core`/`flyway-mysql` 의존성이 없고, `application-local.yml`/`application-prod.yml`에도 `spring.flyway.*` 설정이 없다. 현재는 `docs/integration/database-schema-runbook.md`에 문서화된 대로 **사람이 수동으로 SQL을 실행**하고 `ddl-auto=validate`로만 검증하는 방식이다. Notion 합의안은 `gradle flywayMigrate`/`flywayInfo`/`flywayValidate`를 실제로 쓰는 것을 전제로 하므로, 다음 섹션의 초기 설정 작업이 선행되어야 한다.

## 2. 작업 순서

### 2-1. 인프라 담당(본인) 작업 — `infra/flyway-setup` 브랜치

1. `dev` 기준 `infra/flyway-setup` 브랜치 생성.
2. `backend/build.gradle`에 의존성 추가.
   ```gradle
   implementation 'org.flywaydb:flyway-core'
   implementation 'org.flywaydb:flyway-mysql'
   ```
3. `application-local.yml`, `application-prod.yml`에 설정 추가. `ddl-auto=validate`는 유지(하네스에서 운영 스키마 자동 변경 금지 규정).
   ```yaml
   spring:
     flyway:
       enabled: true
       locations: classpath:db/migration
       baseline-on-migrate: true
   ```
4. `db/schema/member-admin-schema.sql`만 `db/migration/V1__create_base_schema.sql`로 이관한다(내용은 이미 빈 DB 기준 최종 스키마라 그대로 베이스라인이 된다). **`db/manual/V20260721__*.sql`, `V20260722__*.sql`은 옮기지 않는다** — 실제로 열어보면 `DROP TABLE IF EXISTS member_role` / `ALTER TABLE user_account DROP COLUMN member_type`처럼 "구 ERD(comm_v3)를 쓰던 이미 존재하는 DB"에만 1회 적용하는 보정 스크립트라, Flyway가 빈 DB에 그대로 실행하면 존재하지 않는 컬럼을 지우려다 실패한다. 이 두 파일은 `db/manual/`에 그대로 두고 "레거시 DB 전용 수동 스크립트"로 문서화한다(최초 계획에 있던 "db/manual도 이관" 서술은 이 갭 조사 전에 쓴 것이라 정정한다).
   ```
   db/migration/
   ├─ V1__create_base_schema.sql                        (기존 member-admin-schema.sql)
   ├─ V2__create_chat_rtc_call_tables.sql                (chat/rtc/call 도메인)
   ├─ V3__create_inspection_tables.sql                   (inspection 도메인)
   └─ V4__create_payment_refund_settlement_tables.sql    (payment/refund/settlement 도메인)
   db/manual/                                            (레거시 DB 전용, Flyway 미적용 유지)
   ├─ V20260721__member_admin_alignment.sql
   └─ V20260722__member_terms_agreement.sql
   ```
   신규 도메인 SQL은 병합된 Entity의 `@Column`/`@Table` 정의를 그대로 옮겨 작성했다. FK는 Notion 합의(도메인 내부 적용, 교차 도메인은 강결합 예외만)에 따라 `chat_room.buyer_id/seller_id`처럼 이미 예외로 지정된 회원 참조, `payment/refund/settlement`의 회원·관리자 참조에는 FK를 걸고, 아직 존재하지 않는 listing/transaction/evidence 도메인 참조는 FK 없이 컬럼만 두었다.
5. 로컬 빈 MySQL 컨테이너에 `gradle flywayMigrate` 적용 → `gradle flywayInfo`/`flywayValidate`로 확인.
6. `docs/integration/database-schema-runbook.md`를 이 문서로 교체하거나 "Flyway 도입 이후" 절을 추가해 팀원이 참고할 수 있게 갱신(하네스 규정: 구조 변경 시 관련 문서를 같은 작업에서 수정).

### 2-2. `infra/flyway-setup` 브랜치 병합 순서

엔티티 작성 브랜치를 `infra/flyway-setup`에 통합하는 순서는 아래와 같이 고정한다. 뒤 브랜치를 머지하기 전에 앞 브랜치의 충돌·중복 테이블을 먼저 정리해 순서가 꼬이지 않게 한다.

1. `feat/chat-call-rtc-domain` — chat/rtc/call 도메인 전체(가장 넓은 범위)를 먼저 흡수.
2. `feat/chat-core` — chat 도메인의 축소 버전이라 `feat/chat-call-rtc-domain`과 겹치는 `ChatRoom`/`ChatRoomParticipant`/`Member` 계열 중복 여부를 diff로 확인 후 병합.
3. `feat/inspection-core` — 현재 원격 저장소에서 아직 브랜치가 확인되지 않는다(2026-07-22 기준 `git branch -a`/`git ls-remote`에 없음). 병합 전 작성자에게 브랜치 push 여부와 2-3 체크리스트 준수 여부를 먼저 확인한다.
4. `feat/payment-refund-settlement-entity` — 이 브랜치도 현재 원격 저장소에서 확인되지 않는다(2026-07-22 기준). payment/settlement 도메인은 Notion 합의안의 마이그레이션 구조 예시(`V005__create_payment_settlement_tables.sql`)에도 이미 자리가 잡혀 있으므로, push 확인 후 결제·환불·정산 엔티티가 여기 해당하는지 확인하고 병합한다. 회원/결제 등 타 도메인 FK가 있으면 2-3 체크리스트 4번에 따라 별도 마지막 마이그레이션으로 분리 대상인지 표시한다.

각 단계마다 2-3 체크리스트를 적용하고, 병합 후 `gradle flywayMigrate` + `ddl-auto=validate`로 로컬 검증을 통과한 뒤 다음 브랜치로 넘어간다.

### 2-3. 팀원 취합 시 확인 체크리스트

엔티티 작성 브랜치를 리뷰할 때 다음을 확인한다.

1. PK: `@Id @GeneratedValue(strategy = GenerationType.IDENTITY)` — 이미 전원 준수, 신규 엔티티만 재확인.
2. 상태/타입 컬럼: 대응하는 Java `enum` 클래스가 있고 `@Enumerated(EnumType.STRING)`을 붙였는지 확인. 위 1-2절 목록(AdminAccount, AdminActionLog, MemberRestriction, Inquiry, InquiryAnswer, WithdrawalRequest)은 취합 시 enum 클래스 신설 + 필드 타입 교체가 필요함을 작성자에게 공지.
3. DDL 컬럼 타입: `VARCHAR(N)` + enum 값 최대 길이에 맞는 `N` 지정, MySQL 네이티브 `ENUM(...)` 금지.
4. 도메인 간 FK 여부: 하네스 원칙상 도메인 간 Entity 직접 참조 금지이며, FK는 강한 일관성이 꼭 필요한 관계만 예외 허용(Notion 합의 3번 항목). 해당 여부를 표시해 `V007` 마지막 마이그레이션으로 분리할 대상 선별.
5. 기존 마이그레이션 파일(`V20260721__*`, `V20260722__*`) 수정 여부 — 절대 수정 금지, 변경이 필요하면 새 버전 파일 추가.

### 2-4. 최종 병합 순서

1. 도메인별 Flyway SQL 파일과 대응 Entity/Enum 수정을 **같은 PR**로 묶는다.
2. 교차 도메인 FK는 별도 마지막 마이그레이션(`V007` 등)으로 분리해 병합한다.
3. 로컬 빈 MySQL에 전체 마이그레이션 적용 후 `ddl-auto=validate`로 Entity-스키마 매핑을 검증한다.
4. `gradle test` 통과 확인 후 `dev`로 PR을 올린다(스쿼시 머지).
5. 병합 후 각 팀원은 `git pull` 후 로컬에서 `gradle flywayMigrate`(또는 앱 기동)를 직접 실행해야 본인 로컬 DB에 반영됨을 공지 — 자동 반영되지 않음.

## 3. 승인·리스크 메모

- 하네스(`AGENTS.md`) 규정상 **DB migration은 사전 승인 대상**이다. 위 계획 중 실제 `gradle flywayMigrate` 적용, `application-prod.yml` 설정 변경은 이 문서로 통지 후 팀 승인을 받고 진행한다.
- 공유 DB(dev 등)에서 마이그레이션 오류가 나도 `gradle flywayRepair`를 원인 파악 없이 쓰지 않는다(Notion 합의사항, 체크섬 이력 왜곡 위험).
- 운영 DB 적용은 이 계획과 별개로 담당자 검토 및 별도 승인 없이 실행하지 않는다(`database-schema-runbook.md` 기존 규정과 동일).

## 4. 다음 액션

- [x] `infra/flyway-setup` 브랜치 생성 및 2-1 작업 진행 — 완료(아래 5절 참고)
- [x] 2-2 순서(`feat/chat-call-rtc-domain` → `feat/chat-core` → `feat/inspection-core` → `feat/payment-refund-settlement-entity`)대로 병합 — 완료
- [x] `feat/inspection-core`, `feat/payment-refund-settlement-entity` push 확인 — 완료(작업 시점에 원격에 이미 push되어 있었음)
- [ ] AdminAccount/AdminActionLog/MemberRestriction/Inquiry/InquiryAnswer/WithdrawalRequest에 enum 클래스 신설 요청 — 이번 병합 범위(chat/inspection/payment)에는 해당 엔티티가 없어 별도 후속 작업으로 남김
- [x] `db/migration/` 구조 확정 — 완료(V1~V4, 5절 참고)

## 5. 실행 결과 (2026-07-22, GitLab PAT로 직접 작업)

`infra/flyway-setup` 브랜치를 `origin/dev`(893e143) 기준으로 만들고 위 순서대로 4개 브랜치를 병합, Flyway 설정과 신규 도메인 마이그레이션을 커밋한 뒤 push했다. `dev`로 직접 merge하지 않고 MR만 열었다.

- MR: [!21 🚀 infra: Flyway 도입 및 chat/inspection/payment 도메인 병합](https://lab.ssafy.com/s15-webmobile1-sub1/S15P11C203/-/merge_requests/21) (`infra/flyway-setup` → `dev`)
- 병합 중 유일한 충돌은 `feat/chat-core`에서 발생(`ErrorCode.java`, `application.yml`의 springdoc `group-configs`, `BackendApplicationTests`/`OpenApiContractTests`). springdoc 그룹은 `03-chat`이 기존 `03-admin`과 겹쳐 `04-chat`으로 재배정하고 테스트의 `urls.length()` 기대값을 3→4로 수정했다.
- 충돌 해결 중 두 테스트 파일에서 `@MockitoBean` 어노테이션이 누락되는 실수가 있었고(`gradle test`로 실제로 잡힘), 여기에 더해 기존 `AdminAuthorizationTests`(이번 병합과 무관하게 dev에 이미 있던 테스트)도 새로 추가된 `ChatRoomController` 때문에 컨텍스트 로딩이 깨져서 같이 고쳤다.
- 로컬에 JDK 21을 임시 설치해 직접 실행한 검증:
  - `gradle compileJava` 성공
  - `gradle compileTestJava` 성공
  - `gradle test`: 처음 67개 중 10개 실패 → 원인 수정 후 재실행 시 전부 통과
  - `gradle spotlessCheck` 성공
  - `gradle bootJar` 성공
- **미검증(로컬 Docker/MySQL 필요, 이번 작업 환경엔 없음):**
  - `gradle flywayMigrate`/`flywayValidate`를 실제 MySQL에 적용하는 것, `gradle integrationTest`(Testcontainers) — 팀원 로컬 또는 CI에서 반드시 재확인 필요.
  - `java.util.UUID → BINARY(16)`, `@Lob String → LONGTEXT`로 가정하고 SQL을 작성했다. `ddl-auto=validate`가 실패하면 이 타입 매핑부터 확인한다.
- 작업에 사용한 GitLab PAT는 사용 후 로컬 자격 증명 파일에서 제거했다. 만료 전이면 저장소 설정에서 직접 무효화를 권장한다.
