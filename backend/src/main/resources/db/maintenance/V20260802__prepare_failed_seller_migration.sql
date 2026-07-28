-- V20260802가 실패한 운영 seller 스키마를 재실행 가능한 상태로 만든다.
-- 행 데이터는 변경하지 않고, 기존 미사용 컬럼의 신규 행 생성 호환성만 복원한다.
ALTER TABLE seller
    MODIFY COLUMN approved_at DATETIME(6) NULL,
    MODIFY COLUMN product_limit INT NOT NULL DEFAULT 0,
    MODIFY COLUMN sales_amount_limit DECIMAL(38, 2) NOT NULL DEFAULT 0;
