-- 환불(REFUND-05/06) 전용 선행 스키마 보강. 관리자 승인 후 PG 환불 API를
-- 호출하기 전에 미리 생성해 재시도에도 재사용하는 멱등 키를 저장한다.
-- REFUND-*는 구매자·판매자 대면 데모 흐름과 무관한 관리자/내부 배치 전용 기능이라
-- PAY 스키마 보강(V20260724)과 분리한다.

SET @migration_sql = (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE refund_request ADD COLUMN refund_idempotency_key VARCHAR(100) NULL AFTER checklist_item_id',
        'SELECT 1')
    FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'refund_request' AND column_name = 'refund_idempotency_key'
);
PREPARE migration_statement FROM @migration_sql;
EXECUTE migration_statement;
DEALLOCATE PREPARE migration_statement;
