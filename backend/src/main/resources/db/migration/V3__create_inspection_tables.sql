-- inspection 도메인 테이블
-- 근거: com.c203.limit.domain.inspection 패키지 Entity (feat/inspection-core 병합분)
--
-- FK 정책: guide_id는 같은 도메인 내부 참조라 FK를 건다.
--   evidence_id/listing_id는 아직 만들어지지 않은 도메인(증빙/매물)에 대한 참조라 FK를 걸지 않는다.

CREATE TABLE IF NOT EXISTS account_removal_guide (
    account_removal_guide_id BIGINT NOT NULL AUTO_INCREMENT,
    device_type VARCHAR(255) NOT NULL,
    manufacturer VARCHAR(50) NOT NULL,
    template_version INT NOT NULL,
    steps JSON NOT NULL,
    disclaimer_text LONGTEXT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (account_removal_guide_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS battery_report_result (
    battery_report_result_id BIGINT NOT NULL AUTO_INCREMENT,
    evidence_id BIGINT NOT NULL,
    design_capacity VARCHAR(30) NULL,
    full_charge_capacity VARCHAR(30) NULL,
    cycle_count INT NULL,
    battery_manufacturer VARCHAR(50) NULL,
    capacity_ratio DECIMAL(5,2) NULL,
    parser_version VARCHAR(20) NOT NULL,
    parse_status VARCHAR(255) NOT NULL,
    parsed_at DATETIME(6) NOT NULL,
    PRIMARY KEY (battery_report_result_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS dxdiag_result (
    dxdiag_result_id BIGINT NOT NULL AUTO_INCREMENT,
    evidence_id BIGINT NOT NULL,
    cpu VARCHAR(100) NULL,
    memory VARCHAR(50) NULL,
    gpu VARCHAR(100) NULL,
    gpu_memory VARCHAR(30) NULL,
    driver_version VARCHAR(50) NULL,
    sound_device VARCHAR(100) NULL,
    parser_version VARCHAR(20) NOT NULL,
    parse_status VARCHAR(255) NOT NULL,
    parsed_at DATETIME(6) NOT NULL,
    PRIMARY KEY (dxdiag_result_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS listing_account_removal_check (
    listing_account_removal_check_id BIGINT NOT NULL AUTO_INCREMENT,
    listing_id BIGINT NOT NULL,
    guide_id BIGINT NOT NULL,
    seller_confirmed BIT(1) NOT NULL,
    confirmed_at DATETIME(6) NULL,
    screenshot_url VARCHAR(500) NULL,
    PRIMARY KEY (listing_account_removal_check_id),
    CONSTRAINT fk_listing_account_removal_check_guide
        FOREIGN KEY (guide_id) REFERENCES account_removal_guide (account_removal_guide_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS ocr_result (
    ocr_result_id BIGINT NOT NULL AUTO_INCREMENT,
    evidence_id BIGINT NOT NULL,
    field_type VARCHAR(255) NOT NULL,
    raw_text LONGTEXT NULL,
    parsed_value VARCHAR(200) NULL,
    confidence DECIMAL(4,3) NULL,
    ocr_model_version VARCHAR(30) NOT NULL,
    detected_at DATETIME(6) NOT NULL,
    PRIMARY KEY (ocr_result_id),
    INDEX idx_ocr_result_evidence (evidence_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
