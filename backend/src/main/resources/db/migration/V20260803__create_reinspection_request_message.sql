CREATE TABLE IF NOT EXISTS reinspection_request_message (
    reinspection_request_id BIGINT NOT NULL,
    event_type VARCHAR(30) NOT NULL,
    chat_message_id BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (reinspection_request_id, event_type),
    CONSTRAINT uk_reinspection_request_message_chat_message UNIQUE (chat_message_id),
    CONSTRAINT fk_reinspection_request_message_chat_message
        FOREIGN KEY (chat_message_id) REFERENCES chat_message (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
