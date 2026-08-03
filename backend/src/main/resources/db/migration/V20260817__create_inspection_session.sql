-- Windows 무설치 진단 프로그램과 웹 상품 등록 화면을 일회용 코드로 연결한다.
-- pairing code와 agent token은 원문을 저장하지 않고 SHA-256 해시만 보관한다.

CREATE TABLE IF NOT EXISTS inspection_session (
    session_key       CHAR(36)     NOT NULL,
    pairing_code_hash BINARY(32)   NOT NULL,
    seller_id         BIGINT       NOT NULL,
    listing_id        BIGINT       NOT NULL,
    status            VARCHAR(20)  NOT NULL,
    agent_token_hash  BINARY(32)   NULL,
    collector_version VARCHAR(30)  NULL,
    expires_at        DATETIME(6)  NOT NULL,
    created_at        DATETIME(6)  NOT NULL,
    paired_at         DATETIME(6)  NULL,
    completed_at      DATETIME(6)  NULL,
    version           BIGINT       NOT NULL DEFAULT 0,
    PRIMARY KEY (session_key),
    CONSTRAINT fk_inspection_session_listing
        FOREIGN KEY (listing_id) REFERENCES listing (id),
    INDEX idx_inspection_session_pairing (pairing_code_hash, status, expires_at),
    INDEX idx_inspection_session_seller_created (seller_id, created_at),
    INDEX idx_inspection_session_expires (expires_at)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci
  COMMENT = 'Windows 자동 검사 일회용 연결 세션';
