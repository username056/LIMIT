-- 결제 confirm 재시도/복구(fix/payment-confirm-recovery) 선행 스키마 보강.
-- 근거: confirm 호출 시작 시점을 기록해, 예약 만료 배치가 "confirm을 실제로 시도한" 요청 상태
-- 결제만 만료 전 PG 재확인을 거치도록 구분한다. 시도한 적 없는 순수 이탈 건은 기존대로 즉시 만료한다.
-- 이미 적용된 V4, V20260728, V20260808은 수정하지 않고 이 파일에서 ALTER만 추가한다.

SET @migration_sql = (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE payment ADD COLUMN confirm_attempted_at DATETIME(6) NULL AFTER approved_at',
        'SELECT 1')
    FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'payment' AND column_name = 'confirm_attempted_at'
);
PREPARE migration_statement FROM @migration_sql;
EXECUTE migration_statement;
DEALLOCATE PREPARE migration_statement;
