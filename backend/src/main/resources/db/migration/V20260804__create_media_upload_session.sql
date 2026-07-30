CREATE TABLE media_upload_session (
    upload_id CHAR(36) NOT NULL,
    uploader_id BIGINT NOT NULL,
    listing_id BIGINT NOT NULL,
    listing_checklist_item_id BIGINT NULL,
    purpose VARCHAR(30) NOT NULL,
    bucket_name VARCHAR(255) NOT NULL,
    object_key VARCHAR(500) NOT NULL,
    final_object_key VARCHAR(500) NOT NULL,
    original_filename VARCHAR(255) NOT NULL,
    expected_mime_type VARCHAR(100) NOT NULL,
    expected_file_size BIGINT NOT NULL,
    expected_duration_seconds INT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    expires_at DATETIME(6) NOT NULL,
    completed_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (upload_id),
    CONSTRAINT uk_media_upload_session_object UNIQUE (bucket_name, object_key),
    CONSTRAINT uk_media_upload_session_final_object UNIQUE (bucket_name, final_object_key),
    CONSTRAINT fk_media_upload_session_checklist_item
        FOREIGN KEY (listing_checklist_item_id) REFERENCES listing_checklist_item (id),
    INDEX idx_media_upload_session_listing (listing_id),
    INDEX idx_media_upload_session_expiration (status, expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

ALTER TABLE evidence
    ADD CONSTRAINT uk_evidence_s3_key UNIQUE (s3_key);

ALTER TABLE listing_image
    MODIFY COLUMN cdn_url VARCHAR(500) NULL,
    ADD COLUMN display_order INT NOT NULL DEFAULT 0 AFTER image_type,
    ADD CONSTRAINT uk_listing_image_s3_key UNIQUE (s3_key),
    ADD INDEX idx_listing_image_order (listing_id, display_order);
