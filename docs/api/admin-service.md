# 관리자 서비스

관리자 로그인은 일반 회원 인증과 계정 유형을 분리하고 `OPERATOR`, `SUPER_ADMIN` 역할을 JWT에 담는다.
`/api/v1/admin/**`는 두 관리자 역할만 접근할 수 있다. 회원 제한, 판매자 심사·상태·한도 변경,
탈퇴 처리, 문의 답변, 역할 부여·회수는 모두 `admin_action_log`에 기록한다.

## ERD 수정 목록과 이유

| 테이블 | 변경할 컬럼·제약 | 이유 |
| --- | --- | --- |
| `member_sanction` | `restriction_type`, `reason_code`, `reason_detail`, `status`, `release_reason` 추가 | API가 로그인·구매 제한을 구분하고 해제 상태와 사유를 보존해야 함 |
| `admin_action_log` | `before_data JSON`, `after_data JSON`, `ip_address VARCHAR(45)` 추가 | 변경 전후 감사와 요청 주체 추적을 API가 요구함 |
| `seller_application` | `processed_admin_id`를 `reviewer_admin_id` 의미로 사용하거나 이름 변경 | 심사 담당자 선점과 처리자 추적을 명확히 하기 위함 |
| `seller` | 상태값을 `ACTIVE`, `SELLING_RESTRICTED`, `SUSPENDED`, `TERMINATED`로 통일 | 관리자 API의 상태 전이 계약과 ERD ENUM이 다름 |
| `inquiry` | 상태를 `OPEN`, `IN_PROGRESS`, `ANSWERED`, `CLOSED`로 확장하고 `closed_at` 추가 | 답변 전후 처리 흐름과 종료 시각 보존 필요 |
| `inquiry_answer` | `version`, `is_current`, `admin_id`, `created_at` 추가 | 답변 수정 이력을 덮어쓰지 않고 버전으로 보존하기 위함 |
| `withdrawal_request` | 신규 테이블 | 제공된 ERD에 탈퇴 요청·차단·처리자·처리 시각을 저장할 구조가 없음 |
| `member_role` | 신규 테이블, `(user_id, role_code)` UNIQUE | 한 회원에게 구매자·판매자·관리자 역할을 중복 없이 다중 부여하기 위함 |

제공된 ERD SQL은 위 변경 외에도 모든 PK에 `AUTO_INCREMENT`, 이메일·닉네임 유니크 제약,
명시적인 FK와 조회 조건에 맞는 인덱스가 필요하다. 운영 DB 변경은 검토된 Flyway migration으로 별도 적용한다.
