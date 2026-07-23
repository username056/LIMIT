-- payment / refund / settlement 도메인 테이블
-- 근거: com.c203.limit.domain.payment / refund / settlement 패키지 Entity (feat/payment-refund-settlement-entity 병합분)
--
-- FK 정책(Notion 05.DB 합의): 결제·정산은 강한 일관성이 필요한 예외 영역으로 보고 아래 교차 도메인 FK를 건다.
--   - payment.buyer_id, settlement.seller_id -> user_account: 회원 참조, chat_room과 동일한 근거의 예외.
--   - refund_request.payment_id -> payment: 환불은 결제 없이 존재할 수 없는 강결합 관계.
--   - refund_request.processed_admin_id -> admin_account: 처리자 추적을 위한 관리자 참조.
--   listing_id/checklist_item_id는 아직 만들어지지 않은 도메인(매물/체크리스트) 참조라 FK를 걸지 않는다.

CREATE TABLE IF NOT EXISTS payment (
    payment_id BIGINT NOT NULL AUTO_INCREMENT,
    listing_id BIGINT NOT NULL,
    buyer_id BIGINT NOT NULL,
    payment_version INT NOT NULL,
    idempotency_key VARCHAR(100) NOT NULL,
    attempt_no INT NOT NULL,
    pg_provider VARCHAR(20) NOT NULL,
    method VARCHAR(20) NULL,
    provider_transaction_id VARCHAR(200) NULL,
    webhook_verified BIT(1) NOT NULL,
    requested_amount DECIMAL(12,2) NOT NULL,
    approved_amount DECIMAL(12,2) NULL,
    status VARCHAR(30) NOT NULL,
    failed_reason VARCHAR(255) NULL,
    requested_at DATETIME(6) NOT NULL,
    approved_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (payment_id),
    CONSTRAINT uk_payment_idempotency_key UNIQUE (idempotency_key),
    CONSTRAINT fk_payment_buyer FOREIGN KEY (buyer_id) REFERENCES user_account (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS refund_request (
    refund_request_id BIGINT NOT NULL AUTO_INCREMENT,
    payment_id BIGINT NOT NULL,
    reason VARCHAR(500) NOT NULL,
    checklist_item_id BIGINT NULL,
    status VARCHAR(30) NOT NULL,
    reject_reason VARCHAR(500) NULL,
    requested_at DATETIME(6) NOT NULL,
    processed_at DATETIME(6) NULL,
    processed_admin_id BIGINT NULL,
    pg_refund_transaction_id VARCHAR(200) NULL,
    refunded_at DATETIME(6) NULL,
    PRIMARY KEY (refund_request_id),
    CONSTRAINT fk_refund_request_payment FOREIGN KEY (payment_id) REFERENCES payment (payment_id),
    CONSTRAINT fk_refund_request_processed_admin FOREIGN KEY (processed_admin_id) REFERENCES admin_account (admin_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS settlement (
    settlement_id BIGINT NOT NULL AUTO_INCREMENT,
    listing_id BIGINT NOT NULL,
    seller_id BIGINT NOT NULL,
    amount INT NOT NULL,
    status VARCHAR(30) NOT NULL,
    pending_at DATETIME(6) NOT NULL,
    settled_at DATETIME(6) NULL,
    canceled_at DATETIME(6) NULL,
    PRIMARY KEY (settlement_id),
    CONSTRAINT fk_settlement_seller FOREIGN KEY (seller_id) REFERENCES user_account (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
