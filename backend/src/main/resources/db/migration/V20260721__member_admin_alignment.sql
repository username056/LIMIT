-- 기존 운영 스키마를 회원·관리자 Entity와 호환되도록 확장한다.
-- 롤백 호환성과 데이터 보존을 위해 legacy 테이블과 member_type 컬럼은 삭제하지 않는다.

CREATE TABLE IF NOT EXISTS user_account (
    user_id BIGINT NOT NULL AUTO_INCREMENT,
    email VARCHAR(255) NOT NULL,
    password VARCHAR(255) NULL,
    nickname VARCHAR(50) NOT NULL,
    phone VARCHAR(20) NULL,
    status VARCHAR(30) NOT NULL,
    marketing_opt_in BIT(1) NOT NULL DEFAULT b'0',
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (user_id),
    CONSTRAINT uk_user_account_email UNIQUE (email),
    CONSTRAINT uk_user_account_nickname UNIQUE (nickname)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

SET @migration_sql = (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE user_account ADD COLUMN email_verified_at DATETIME(6) NULL',
        'SELECT 1')
    FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'user_account' AND column_name = 'email_verified_at'
);
PREPARE migration_statement FROM @migration_sql;
EXECUTE migration_statement;
DEALLOCATE PREPARE migration_statement;

SET @migration_sql = (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE user_account ADD COLUMN last_login_at DATETIME(6) NULL',
        'SELECT 1')
    FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'user_account' AND column_name = 'last_login_at'
);
PREPARE migration_statement FROM @migration_sql;
EXECUTE migration_statement;
DEALLOCATE PREPARE migration_statement;

SET @migration_sql = (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE user_account ADD COLUMN password_changed_at DATETIME(6) NULL',
        'SELECT 1')
    FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'user_account' AND column_name = 'password_changed_at'
);
PREPARE migration_statement FROM @migration_sql;
EXECUTE migration_statement;
DEALLOCATE PREPARE migration_statement;

SET @migration_sql = (
    SELECT IF(COUNT(*) = 1,
        'ALTER TABLE user_account MODIFY COLUMN member_type VARCHAR(20) NULL DEFAULT ''MEMBER''',
        'SELECT 1')
    FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'user_account' AND column_name = 'member_type'
);
PREPARE migration_statement FROM @migration_sql;
EXECUTE migration_statement;
DEALLOCATE PREPARE migration_statement;

CREATE TABLE IF NOT EXISTS admin_account (
    admin_id BIGINT NOT NULL AUTO_INCREMENT,
    email VARCHAR(255) NOT NULL,
    password VARCHAR(255) NOT NULL,
    name VARCHAR(255) NOT NULL,
    role VARCHAR(255) NOT NULL,
    status VARCHAR(255) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (admin_id),
    CONSTRAINT uk_admin_account_email UNIQUE (email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

SET @migration_sql = (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE admin_account ADD COLUMN updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)',
        'SELECT 1')
    FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'admin_account' AND column_name = 'updated_at'
);
PREPARE migration_statement FROM @migration_sql;
EXECUTE migration_statement;
DEALLOCATE PREPARE migration_statement;

SET @migration_sql = (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE admin_account ADD COLUMN last_login_at DATETIME(6) NULL',
        'SELECT 1')
    FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'admin_account' AND column_name = 'last_login_at'
);
PREPARE migration_statement FROM @migration_sql;
EXECUTE migration_statement;
DEALLOCATE PREPARE migration_statement;

SET @migration_sql = (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE admin_account ADD COLUMN password_changed_at DATETIME(6) NULL',
        'SELECT 1')
    FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'admin_account' AND column_name = 'password_changed_at'
);
PREPARE migration_statement FROM @migration_sql;
EXECUTE migration_statement;
DEALLOCATE PREPARE migration_statement;

CREATE TABLE IF NOT EXISTS social_account (
    social_account_id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    provider VARCHAR(20) NOT NULL,
    provider_user_id VARCHAR(255) NOT NULL,
    provider_email VARCHAR(255) NULL,
    linked_at DATETIME(6) NOT NULL,
    PRIMARY KEY (social_account_id),
    CONSTRAINT uk_social_provider_user UNIQUE (provider, provider_user_id),
    CONSTRAINT fk_social_account_user FOREIGN KEY (user_id) REFERENCES user_account (user_id),
    INDEX idx_social_account_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

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
    CONSTRAINT fk_member_sanction_user FOREIGN KEY (user_id) REFERENCES user_account (user_id),
    INDEX idx_member_sanction_active (user_id, restriction_type, status),
    INDEX idx_member_sanction_period (start_at, end_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS admin_action_log (
    log_id BIGINT NOT NULL AUTO_INCREMENT,
    admin_id BIGINT NOT NULL,
    action_type VARCHAR(255) NOT NULL,
    target_type VARCHAR(255) NOT NULL,
    target_id BIGINT NOT NULL,
    reason VARCHAR(500) NULL,
    before_data JSON NULL,
    after_data JSON NULL,
    ip_address VARCHAR(45) NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (log_id),
    INDEX idx_admin_action_log_admin_created (admin_id, created_at),
    INDEX idx_admin_action_log_target (target_type, target_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
