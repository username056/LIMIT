-- Toss 결제 식별자(providerOrderId) 연결 선행 스키마 보강.
-- 근거: 재시도 시 attemptNo 기준으로 새 provider_order_id를 발급해 Toss 결제창 세션과 매핑한다.
-- payment_id는 IDENTITY 채번이라 최초 insert 시점엔 알 수 없으므로 컬럼은 nullable로 두고,
-- 애플리케이션이 같은 트랜잭션 안에서 저장 직후 값을 채운 뒤 커밋한다(커밋 전 NULL 상태는
-- 트랜잭션 격리로 다른 트랜잭션에 노출되지 않는다). UNIQUE 제약은 MySQL에서 NULL 다중 허용이라
-- 이 일시적 NULL과 충돌하지 않는다.
-- 이미 적용된 V4, V20260728은 수정하지 않고 이 파일에서 ALTER만 추가한다.

SET @migration_sql = (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE payment ADD COLUMN provider_order_id VARCHAR(64) NULL AFTER attempt_no',
        'SELECT 1')
    FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'payment' AND column_name = 'provider_order_id'
);
PREPARE migration_statement FROM @migration_sql;
EXECUTE migration_statement;
DEALLOCATE PREPARE migration_statement;

UPDATE payment
SET provider_order_id = CONCAT('PAY-', payment_id, '-', attempt_no)
WHERE provider_order_id IS NULL;

SET @migration_sql = (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE payment ADD CONSTRAINT uk_payment_provider_order_id UNIQUE (provider_order_id)',
        'SELECT 1')
    FROM information_schema.statistics
    WHERE table_schema = DATABASE() AND table_name = 'payment' AND index_name = 'uk_payment_provider_order_id'
);
PREPARE migration_statement FROM @migration_sql;
EXECUTE migration_statement;
DEALLOCATE PREPARE migration_statement;
