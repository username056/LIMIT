# 회원·관리자 DB 스키마 적용 Runbook

이 프로젝트는 Flyway를 자동 실행하지 않고 모든 환경에서 Hibernate `ddl-auto=validate`를 사용한다. 애플리케이션 시작 전에 검토된 SQL을 사람이 적용해야 한다.

## SQL 구분

- `backend/src/main/resources/db/schema/member-admin-schema.sql`: 빈 로컬·테스트 DB의 기준 스키마
- `backend/src/main/resources/db/manual/V20260721__member_admin_alignment.sql`: 기존 회원·관리자 스키마 정렬
- `backend/src/main/resources/db/manual/V20260722__member_terms_agreement.sql`: 약관 동의 이력 테이블 추가

Docker MySQL의 `/docker-entrypoint-initdb.d` 스크립트는 `mysql-data` 볼륨을 처음 만들 때만 실행된다. 기존 볼륨에는 다시 적용되지 않는다.

## 기존 DB 적용 순서

1. 대상 DB와 적용 시각을 확정하고 복구 가능한 백업을 만든다.
2. `information_schema`로 현재 테이블·컬럼·제약조건을 확인한다.
3. 데이터 보존이 필요한 `member_role`, `user_sanction`, `user_account.member_type`의 정리 방식을 확정한다.
4. 이미 존재하는 컬럼이나 제약조건은 수동 SQL에서 제거하거나 이름을 맞춘다.
5. `V20260721__member_admin_alignment.sql`이 필요한 환경은 먼저 적용한다.
6. `V20260722__member_terms_agreement.sql`을 적용한다.
7. 애플리케이션을 시작하고 `ddl-auto=validate`와 readiness가 통과하는지 확인한다.
8. 회원가입 후 `member_terms_agreement`에 필수 3건과 마케팅 1건이 기록되는지 확인한다.

운영 DB 적용은 별도 승인과 담당자 검토 없이 실행하지 않는다.

## 환경변수 파일

- 로컬: Git에서 제외된 `infra/.env.local`
- 운영: 서버의 `/etc/limit/prod.env` 같은 권한 제한 파일 또는 CI protected variable

```powershell
docker compose --env-file infra/.env.local -p limit-local -f infra/compose.yml -f infra/compose.local.yml up -d
```

실제 `.env`, Secret, 토큰, 키는 저장소에 커밋하지 않는다. `.env.example`에는 이름과 안전한 예시만 둔다.
