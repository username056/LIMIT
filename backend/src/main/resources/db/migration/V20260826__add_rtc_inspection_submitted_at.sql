ALTER TABLE rtc_session
    ADD COLUMN inspection_submitted_at DATETIME(6) NULL AFTER connected_at;
