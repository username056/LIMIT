ALTER TABLE rtc_session
    ADD COLUMN verification_memo VARCHAR(1000) NULL AFTER end_reason,
    ADD CONSTRAINT uk_rtc_session_appointment UNIQUE (call_appointment_id),
    ADD INDEX ix_rtc_session_participants_status (seller_id, buyer_id, status);

CREATE TABLE rtc_session_checklist_result (
    id BIGINT NOT NULL AUTO_INCREMENT,
    rtc_session_id BIGINT NOT NULL,
    listing_checklist_item_id BIGINT NOT NULL,
    checked_by BIGINT NOT NULL,
    is_confirmed BIT(1) NOT NULL,
    note VARCHAR(500) NULL,
    checked_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_rtc_checklist_session_item UNIQUE (rtc_session_id, listing_checklist_item_id),
    CONSTRAINT fk_rtc_checklist_session FOREIGN KEY (rtc_session_id) REFERENCES rtc_session (id),
    CONSTRAINT fk_rtc_checklist_item FOREIGN KEY (listing_checklist_item_id) REFERENCES listing_checklist_item (id),
    CONSTRAINT fk_rtc_checklist_member FOREIGN KEY (checked_by) REFERENCES user_account (user_id),
    INDEX ix_rtc_checklist_session (rtc_session_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
