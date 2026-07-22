-- 운영 적용 전 DB 백업과 현재 스키마를 확인한 뒤 수동 실행한다.
-- 이 파일은 Flyway에서 자동 실행되지 않는다.

CREATE TABLE IF NOT EXISTS member_terms_agreement (
    agreement_id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    terms_code VARCHAR(30) NOT NULL,
    terms_version VARCHAR(30) NOT NULL,
    is_agreed BIT(1) NOT NULL,
    agreed_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (agreement_id),
    CONSTRAINT uk_member_terms_version UNIQUE (user_id, terms_code, terms_version),
    CONSTRAINT fk_member_terms_user FOREIGN KEY (user_id) REFERENCES user_account (user_id),
    INDEX idx_member_terms_user_created_at (user_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
