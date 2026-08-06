ALTER TABLE listing
    ADD COLUMN moderation_status VARCHAR(30) NOT NULL DEFAULT 'NORMAL' AFTER status,
    ADD INDEX idx_listing_public_moderation (status, moderation_status, deleted_at, created_at),
    ADD INDEX idx_listing_seller_moderation (seller_id, moderation_status, updated_at);

ALTER TABLE listing_image
    ADD COLUMN content_sha256 CHAR(64) NULL AFTER mime_type,
    ADD COLUMN perceptual_hash CHAR(16) NULL AFTER content_sha256,
    ADD COLUMN analyzed_at DATETIME(6) NULL AFTER perceptual_hash,
    ADD INDEX idx_listing_image_content_hash (content_sha256),
    ADD INDEX idx_listing_image_perceptual_hash (perceptual_hash);

CREATE TABLE listing_report (
    report_id BIGINT NOT NULL AUTO_INCREMENT,
    listing_id BIGINT NOT NULL,
    reporter_id BIGINT NOT NULL,
    category VARCHAR(40) NOT NULL,
    detail VARCHAR(1000) NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    reviewer_admin_id BIGINT NULL,
    admin_note VARCHAR(1000) NULL,
    reviewed_at DATETIME(6) NULL,
    acknowledged_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (report_id),
    CONSTRAINT fk_listing_report_listing
        FOREIGN KEY (listing_id) REFERENCES listing (id),
    CONSTRAINT fk_listing_report_reporter
        FOREIGN KEY (reporter_id) REFERENCES user_account (user_id),
    CONSTRAINT uk_listing_report_reporter UNIQUE (listing_id, reporter_id),
    INDEX idx_listing_report_status_created (status, created_at),
    INDEX idx_listing_report_listing_status (listing_id, status)
);

CREATE TABLE listing_restoration_request (
    restoration_request_id BIGINT NOT NULL AUTO_INCREMENT,
    listing_id BIGINT NOT NULL,
    seller_id BIGINT NOT NULL,
    request_note VARCHAR(1000) NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    reviewer_admin_id BIGINT NULL,
    review_note VARCHAR(1000) NULL,
    reviewed_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (restoration_request_id),
    CONSTRAINT fk_listing_restoration_listing
        FOREIGN KEY (listing_id) REFERENCES listing (id),
    CONSTRAINT fk_listing_restoration_seller
        FOREIGN KEY (seller_id) REFERENCES user_account (user_id),
    INDEX idx_listing_restoration_status_created (status, created_at),
    INDEX idx_listing_restoration_listing_status (listing_id, status)
);

CREATE TABLE moderation_risk_signal (
    risk_signal_id BIGINT NOT NULL AUTO_INCREMENT,
    seller_id BIGINT NOT NULL,
    listing_id BIGINT NULL,
    related_listing_id BIGINT NULL,
    signal_type VARCHAR(40) NOT NULL,
    score INT NOT NULL,
    detail VARCHAR(500) NOT NULL,
    fingerprint VARCHAR(180) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'OPEN',
    resolved_by_admin_id BIGINT NULL,
    resolution_note VARCHAR(500) NULL,
    resolved_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (risk_signal_id),
    CONSTRAINT fk_moderation_risk_seller
        FOREIGN KEY (seller_id) REFERENCES user_account (user_id),
    CONSTRAINT fk_moderation_risk_listing
        FOREIGN KEY (listing_id) REFERENCES listing (id),
    CONSTRAINT fk_moderation_risk_related_listing
        FOREIGN KEY (related_listing_id) REFERENCES listing (id),
    CONSTRAINT uk_moderation_risk_fingerprint UNIQUE (fingerprint),
    INDEX idx_moderation_risk_status_created (status, created_at),
    INDEX idx_moderation_risk_seller_status (seller_id, status)
);
