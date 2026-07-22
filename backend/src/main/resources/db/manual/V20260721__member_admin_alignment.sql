-- 수동 실행 전 반드시 DB 백업과 대상 스키마 확인을 수행한다.
-- 이 파일은 Flyway가 자동 실행하지 않는다. 구 ERD(comm_v3)에서 1회 적용하는 변경 SQL이다.

DROP TABLE IF EXISTS member_role;
DROP TABLE IF EXISTS user_sanction;

ALTER TABLE user_account
    DROP COLUMN member_type,
    ADD COLUMN email_verified_at DATETIME(6) NULL,
    ADD COLUMN last_login_at DATETIME(6) NULL,
    ADD COLUMN password_changed_at DATETIME(6) NULL;

ALTER TABLE admin_account
    ADD COLUMN updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    ADD COLUMN last_login_at DATETIME(6) NULL,
    ADD COLUMN password_changed_at DATETIME(6) NULL;

CREATE TABLE IF NOT EXISTS member_sanction (
    sanction_id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    restriction_type VARCHAR(255) NOT NULL,
    reason_code VARCHAR(255) NOT NULL,
    reason_detail VARCHAR(500) NOT NULL,
    status VARCHAR(255) NOT NULL,
    start_at DATETIME(6) NOT NULL,
    end_at DATETIME(6) NOT NULL,
    admin_id BIGINT NOT NULL,
    released_at DATETIME(6) NULL,
    release_admin_id BIGINT NULL,
    release_reason VARCHAR(500) NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (sanction_id),
    CONSTRAINT fk_member_sanction_user
        FOREIGN KEY (user_id) REFERENCES user_account (user_id),
    INDEX idx_member_sanction_active (user_id, restriction_type, status),
    INDEX idx_member_sanction_period (start_at, end_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 나머지 신규 테이블과 제약은 db/schema/member-admin-schema.sql을 기준으로 ERD와 대조한다.
-- 이미 같은 이름의 컬럼이 있는 DB에는 해당 ADD COLUMN 절을 제거한 뒤 검토 실행한다.
