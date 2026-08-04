-- Windows 선택검사 결과 이력. 원본 영상·음성은 저장하지 않고 구조화된 측정값만 저장한다.

CREATE TABLE IF NOT EXISTS inspection_session_test_result (
    id                        BIGINT       NOT NULL AUTO_INCREMENT,
    session_key               CHAR(36)     NOT NULL,
    listing_checklist_item_id BIGINT       NULL,
    client_result_id          BINARY(16)   NOT NULL,
    test_type                 VARCHAR(30)  NOT NULL,
    measurement_status        VARCHAR(30)  NOT NULL,
    user_result               VARCHAR(30)  NULL,
    measured_values           JSON         NULL,
    attempt_no                INT UNSIGNED NOT NULL,
    raw_data_saved            BOOLEAN      NOT NULL DEFAULT FALSE,
    tested_at                 DATETIME(6)  NOT NULL,
    created_at                DATETIME(6)  NOT NULL,
    error_code                VARCHAR(100) NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_inspection_test_result_client
        UNIQUE (session_key, client_result_id),
    CONSTRAINT uk_inspection_test_result_attempt
        UNIQUE (session_key, test_type, attempt_no),
    CONSTRAINT fk_inspection_test_result_session
        FOREIGN KEY (session_key) REFERENCES inspection_session (session_key),
    CONSTRAINT fk_inspection_test_result_checklist_item
        FOREIGN KEY (listing_checklist_item_id) REFERENCES listing_checklist_item (id),
    INDEX idx_inspection_test_result_session_created (session_key, created_at, id),
    INDEX idx_inspection_test_result_checklist_item (listing_checklist_item_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci
  COMMENT = 'Windows 선택검사 결과 이력';
