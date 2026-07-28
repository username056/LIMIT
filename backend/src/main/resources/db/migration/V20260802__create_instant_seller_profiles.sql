-- 판매자 심사 없이 회원이 즉시 ACTIVE 판매자로 등록할 수 있는 프로필 스키마를 구성한다.
-- 기존 seller 테이블과 user_account.member_type 데이터는 삭제하지 않고 호환 컬럼으로 흡수한다.

CREATE TABLE IF NOT EXISTS seller (
    seller_id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    seller_type VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    country_code VARCHAR(2) NOT NULL,
    business_name VARCHAR(100) NULL,
    settlement_bank_name VARCHAR(100) NOT NULL,
    settlement_account_holder VARCHAR(100) NOT NULL,
    settlement_account_last4 VARCHAR(4) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (seller_id),
    CONSTRAINT uk_seller_user UNIQUE (user_id),
    INDEX idx_seller_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

SET @migration_sql = (
    SELECT IFNULL(
        CONCAT(
            'ALTER TABLE seller ',
            GROUP_CONCAT(required_column.column_sql
                ORDER BY required_column.ordinal SEPARATOR ', ')
        ),
        'SELECT 1')
    FROM (
        SELECT 1 AS ordinal, 'user_id' AS column_name,
            'ADD COLUMN user_id BIGINT NULL' AS column_sql
        UNION ALL SELECT 2, 'seller_type',
            'ADD COLUMN seller_type VARCHAR(20) NULL'
        UNION ALL SELECT 3, 'country_code',
            'ADD COLUMN country_code VARCHAR(2) NULL'
        UNION ALL SELECT 4, 'business_name',
            'ADD COLUMN business_name VARCHAR(100) NULL'
        UNION ALL SELECT 5, 'settlement_bank_name',
            'ADD COLUMN settlement_bank_name VARCHAR(100) NULL'
        UNION ALL SELECT 6, 'settlement_account_holder',
            'ADD COLUMN settlement_account_holder VARCHAR(100) NULL'
        UNION ALL SELECT 7, 'settlement_account_last4',
            'ADD COLUMN settlement_account_last4 VARCHAR(4) NULL'
        UNION ALL SELECT 8, 'created_at',
            'ADD COLUMN created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)'
        UNION ALL SELECT 9, 'updated_at',
            'ADD COLUMN updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)'
    ) required_column
    LEFT JOIN information_schema.columns existing_column
        ON existing_column.table_schema = DATABASE()
       AND existing_column.table_name = 'seller'
       AND existing_column.column_name = required_column.column_name
    WHERE existing_column.column_name IS NULL
);
PREPARE migration_statement FROM @migration_sql;
EXECUTE migration_statement;
DEALLOCATE PREPARE migration_statement;

SET @migration_sql = (
    SELECT IF(COUNT(*) = 1,
        'UPDATE seller SET seller_type = COALESCE(seller_type, seller_category)',
        'SELECT 1')
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'seller'
      AND column_name = 'seller_category'
);
PREPARE migration_statement FROM @migration_sql;
EXECUTE migration_statement;
DEALLOCATE PREPARE migration_statement;

SET @migration_sql = (
    SELECT IF(COUNT(*) = 1,
        'UPDATE seller SET country_code = COALESCE(country_code, LEFT(UPPER(country), 2))',
        'SELECT 1')
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'seller'
      AND column_name = 'country'
);
PREPARE migration_statement FROM @migration_sql;
EXECUTE migration_statement;
DEALLOCATE PREPARE migration_statement;

SET @migration_sql = (
    SELECT IF(COUNT(*) = 1,
        'UPDATE seller SET business_name = COALESCE(business_name, seller_name)',
        'SELECT 1')
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'seller'
      AND column_name = 'seller_name'
);
PREPARE migration_statement FROM @migration_sql;
EXECUTE migration_statement;
DEALLOCATE PREPARE migration_statement;

UPDATE seller
SET seller_type = COALESCE(NULLIF(seller_type, ''), 'INDIVIDUAL'),
    status = COALESCE(NULLIF(status, ''), 'ACTIVE'),
    country_code = COALESCE(NULLIF(country_code, ''), 'KR'),
    settlement_bank_name = COALESCE(NULLIF(settlement_bank_name, ''), 'UNKNOWN'),
    settlement_account_holder =
        COALESCE(NULLIF(settlement_account_holder, ''), 'UNKNOWN'),
    settlement_account_last4 =
        COALESCE(NULLIF(settlement_account_last4, ''), '0000');

ALTER TABLE seller
    MODIFY COLUMN seller_type VARCHAR(20) NOT NULL,
    MODIFY COLUMN status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    MODIFY COLUMN country_code VARCHAR(2) NOT NULL,
    MODIFY COLUMN settlement_bank_name VARCHAR(100) NOT NULL,
    MODIFY COLUMN settlement_account_holder VARCHAR(100) NOT NULL,
    MODIFY COLUMN settlement_account_last4 VARCHAR(4) NOT NULL;

SET @migration_sql = (
    SELECT IF(COUNT(*) = 1,
        'ALTER TABLE seller MODIFY COLUMN seller_name VARCHAR(100) NULL',
        'SELECT 1')
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'seller'
      AND column_name = 'seller_name'
);
PREPARE migration_statement FROM @migration_sql;
EXECUTE migration_statement;
DEALLOCATE PREPARE migration_statement;

SET @migration_sql = (
    SELECT IF(COUNT(*) = 1,
        'ALTER TABLE seller MODIFY COLUMN seller_category VARCHAR(30) NULL',
        'SELECT 1')
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'seller'
      AND column_name = 'seller_category'
);
PREPARE migration_statement FROM @migration_sql;
EXECUTE migration_statement;
DEALLOCATE PREPARE migration_statement;

SET @migration_sql = (
    SELECT IF(COUNT(*) = 1,
        'ALTER TABLE seller MODIFY COLUMN country VARCHAR(50) NULL',
        'SELECT 1')
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'seller'
      AND column_name = 'country'
);
PREPARE migration_statement FROM @migration_sql;
EXECUTE migration_statement;
DEALLOCATE PREPARE migration_statement;

SET @migration_sql = (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE seller ADD CONSTRAINT uk_seller_user UNIQUE (user_id)',
        'SELECT 1')
    FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'seller'
      AND column_name = 'user_id'
      AND non_unique = 0
);
PREPARE migration_statement FROM @migration_sql;
EXECUTE migration_statement;
DEALLOCATE PREPARE migration_statement;

SET @migration_sql = (
    SELECT IF(COUNT(*) = 1,
        'INSERT INTO seller (
            user_id, seller_type, status, country_code, business_name,
            settlement_bank_name, settlement_account_holder,
            settlement_account_last4, created_at, updated_at
         )
         SELECT
            account.user_id, ''INDIVIDUAL'', ''ACTIVE'', ''KR'', account.nickname,
            ''UNKNOWN'', ''UNKNOWN'', ''0000'',
            COALESCE(account.created_at, CURRENT_TIMESTAMP(6)), CURRENT_TIMESTAMP(6)
         FROM user_account account
         LEFT JOIN seller profile ON profile.user_id = account.user_id
         WHERE account.member_type = ''SELLER''
           AND profile.seller_id IS NULL',
        'SELECT 1')
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'user_account'
      AND column_name = 'member_type'
);
PREPARE migration_statement FROM @migration_sql;
EXECUTE migration_statement;
DEALLOCATE PREPARE migration_statement;
