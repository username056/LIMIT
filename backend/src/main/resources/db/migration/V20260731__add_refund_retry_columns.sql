-- REFUND-06 (환불 실패 재시도) 선행 스키마 보강.
-- PG 환불 API 호출 실패 시 지수 백오프 재시도를 DB 컬럼 기반 폴링으로 관리한다.
-- 근거: 결제/환불은 신뢰성이 최우선이라 큐 대신 MySQL 트랜잭션 보장을 사용하기로 결정.
-- 이미 적용된 V4, V20260724, V20260725는 수정하지 않고 이 파일에서 ALTER만 추가한다.

SET @migration_sql = (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE refund_request ADD COLUMN retry_count INT NOT NULL DEFAULT 0 AFTER refund_idempotency_key',
        'SELECT 1')
    FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'refund_request' AND column_name = 'retry_count'
);
PREPARE migration_statement FROM @migration_sql;
EXECUTE migration_statement;
DEALLOCATE PREPARE migration_statement;

SET @migration_sql = (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE refund_request ADD COLUMN next_retry_at DATETIME(6) NULL AFTER retry_count',
        'SELECT 1')
    FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'refund_request' AND column_name = 'next_retry_at'
);
PREPARE migration_statement FROM @migration_sql;
EXECUTE migration_statement;
DEALLOCATE PREPARE migration_statement;
