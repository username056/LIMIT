CREATE TABLE device_model_request (
    id BIGINT NOT NULL AUTO_INCREMENT,
    requested_by_member_id BIGINT NOT NULL,
    parent_category_id BIGINT NOT NULL,
    manufacturer VARCHAR(50) NOT NULL,
    model_name VARCHAR(100) NOT NULL,
    model_code VARCHAR(50) NULL,
    os_family VARCHAR(30) NOT NULL,
    status VARCHAR(30) NOT NULL,
    resolved_category_id BIGINT NULL,
    reviewed_by_admin_id BIGINT NULL,
    review_note VARCHAR(500) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_device_model_request_category
        FOREIGN KEY (parent_category_id) REFERENCES category (id),
    CONSTRAINT fk_device_model_request_resolved_category
        FOREIGN KEY (resolved_category_id) REFERENCES category (id),
    INDEX idx_device_model_request_status_created (status, created_at),
    INDEX idx_device_model_request_requester (requested_by_member_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
