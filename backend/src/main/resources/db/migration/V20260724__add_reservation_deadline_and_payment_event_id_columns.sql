-- 결제 MVP 1차(PAY-01~13) 착수 전 선행 스키마 보강.
-- 근거: PAY-01/03/07 (예약 만료), PAY-10/12 (전달완료 후 자동 구매확정),
--       PAY-04 (웹훅 pg_event_id 중복 방지) 요구사항.
-- 환불(REFUND-*) 전용 컬럼은 V20260725에서 별도로 추가한다.
-- listing 컬럼 3개는 product 도메인 담당자 승인을 받고 추가한다.
-- 이미 적용된 V4, V20260723은 수정하지 않고 이 파일에서 ALTER만 추가한다.

SET @migration_sql = (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE listing ADD COLUMN reserved_until DATETIME(6) NULL AFTER reserved_at',
        'SELECT 1')
    FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'listing' AND column_name = 'reserved_until'
);
PREPARE migration_statement FROM @migration_sql;
EXECUTE migration_statement;
DEALLOCATE PREPARE migration_statement;

SET @migration_sql = (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE listing ADD COLUMN handed_over_at DATETIME(6) NULL AFTER paid_at',
        'SELECT 1')
    FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'listing' AND column_name = 'handed_over_at'
);
PREPARE migration_statement FROM @migration_sql;
EXECUTE migration_statement;
DEALLOCATE PREPARE migration_statement;

SET @migration_sql = (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE listing ADD COLUMN auto_confirm_at DATETIME(6) NULL AFTER handed_over_at',
        'SELECT 1')
    FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'listing' AND column_name = 'auto_confirm_at'
);
PREPARE migration_statement FROM @migration_sql;
EXECUTE migration_statement;
DEALLOCATE PREPARE migration_statement;

SET @migration_sql = (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE payment ADD COLUMN pg_event_id VARCHAR(200) NULL AFTER provider_transaction_id',
        'SELECT 1')
    FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'payment' AND column_name = 'pg_event_id'
);
PREPARE migration_statement FROM @migration_sql;
EXECUTE migration_statement;
DEALLOCATE PREPARE migration_statement;

SET @migration_sql = (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE payment ADD CONSTRAINT uk_payment_pg_event_id UNIQUE (pg_event_id)',
        'SELECT 1')
    FROM information_schema.statistics
    WHERE table_schema = DATABASE() AND table_name = 'payment' AND index_name = 'uk_payment_pg_event_id'
);
PREPARE migration_statement FROM @migration_sql;
EXECUTE migration_statement;
DEALLOCATE PREPARE migration_statement;
