-- chat / rtc / call 도메인 테이블
-- 근거: com.c203.limit.domain.chat / rtc / call 패키지 Entity (feat/chat-call-rtc-domain, feat/chat-core 병합분)
--
-- 타입 매핑 주의(로컬 ddl-auto=validate로 재검증 필요):
--   - java.util.UUID 컬럼은 Hibernate 6 + MySQL 기본 매핑을 따라 BINARY(16)으로 작성했다.
--   - @Lob String 컬럼은 MySQL에서 LONGTEXT로 매핑됨을 가정했다.
--
-- FK 정책(Notion 05.DB 합의): 도메인 내부 FK는 적용, 도메인 간 FK는 강한 일관성이 필요한 관계만 예외 적용.
--   - chat_room.buyer_id/seller_id 등 회원 참조는 원 합의문에서 이미 예외로 지정된 케이스라 FK를 건다.
--   - chat_outbox_event는 로그성 Outbox 테이블이라 FK를 걸지 않는다.
--   - listing_id/transaction_id/evidence_id 등 아직 만들어지지 않은 도메인(listing/transaction 등)에 대한 참조는 FK를 걸지 않는다.

CREATE TABLE IF NOT EXISTS chat_room (
    id BIGINT NOT NULL AUTO_INCREMENT,
    listing_id BIGINT NOT NULL,
    transaction_id BIGINT NULL,
    buyer_id BIGINT NOT NULL,
    seller_id BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL,
    last_message_id BIGINT NULL,
    last_message_seq BIGINT NOT NULL,
    last_message_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    closed_at DATETIME(6) NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_chat_room_listing_users UNIQUE (listing_id, buyer_id, seller_id),
    CONSTRAINT fk_chat_room_buyer FOREIGN KEY (buyer_id) REFERENCES user_account (user_id),
    CONSTRAINT fk_chat_room_seller FOREIGN KEY (seller_id) REFERENCES user_account (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS chat_room_participant (
    id BIGINT NOT NULL AUTO_INCREMENT,
    chat_room_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    participant_role VARCHAR(20) NOT NULL,
    last_read_seq BIGINT NOT NULL,
    joined_at DATETIME(6) NOT NULL,
    left_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_chat_room_participant_user UNIQUE (chat_room_id, user_id),
    CONSTRAINT uk_chat_room_participant_role UNIQUE (chat_room_id, participant_role),
    CONSTRAINT fk_chat_room_participant_room FOREIGN KEY (chat_room_id) REFERENCES chat_room (id),
    CONSTRAINT fk_chat_room_participant_user FOREIGN KEY (user_id) REFERENCES user_account (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS chat_message (
    id BIGINT NOT NULL AUTO_INCREMENT,
    chat_room_id BIGINT NOT NULL,
    room_sequence BIGINT NOT NULL,
    sender_id BIGINT NOT NULL,
    client_message_id BINARY(16) NOT NULL,
    message_type VARCHAR(20) NOT NULL,
    content LONGTEXT NULL,
    status VARCHAR(20) NOT NULL,
    sent_at DATETIME(6) NOT NULL,
    deleted_at DATETIME(6) NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_chat_message_room_sequence UNIQUE (chat_room_id, room_sequence),
    CONSTRAINT uk_chat_message_client UNIQUE (chat_room_id, client_message_id),
    CONSTRAINT fk_chat_message_room FOREIGN KEY (chat_room_id) REFERENCES chat_room (id),
    CONSTRAINT fk_chat_message_sender FOREIGN KEY (sender_id) REFERENCES user_account (user_id),
    INDEX ix_chat_message_room_sent (chat_room_id, sent_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS chat_media (
    id BIGINT NOT NULL AUTO_INCREMENT,
    media_key BINARY(16) NOT NULL,
    chat_room_id BIGINT NOT NULL,
    uploader_id BIGINT NOT NULL,
    media_type VARCHAR(20) NOT NULL,
    upload_status VARCHAR(20) NOT NULL,
    bucket_name VARCHAR(255) NOT NULL,
    object_key VARCHAR(1024) NOT NULL,
    original_filename VARCHAR(255) NOT NULL,
    mime_type VARCHAR(100) NOT NULL,
    file_size_bytes BIGINT NOT NULL,
    width_px INT NULL,
    height_px INT NULL,
    duration_ms BIGINT NULL,
    upload_expires_at DATETIME(6) NOT NULL,
    verified_at DATETIME(6) NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_chat_media_key UNIQUE (media_key),
    CONSTRAINT uk_chat_media_object UNIQUE (bucket_name, object_key(255)),
    CONSTRAINT fk_chat_media_room FOREIGN KEY (chat_room_id) REFERENCES chat_room (id),
    CONSTRAINT fk_chat_media_uploader FOREIGN KEY (uploader_id) REFERENCES user_account (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS chat_message_media (
    chat_message_id BIGINT NOT NULL,
    chat_media_id BIGINT NOT NULL,
    display_order INT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (chat_message_id, chat_media_id),
    CONSTRAINT fk_chat_message_media_message FOREIGN KEY (chat_message_id) REFERENCES chat_message (id),
    CONSTRAINT fk_chat_message_media_media FOREIGN KEY (chat_media_id) REFERENCES chat_media (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS chat_outbox_event (
    id BIGINT NOT NULL AUTO_INCREMENT,
    event_id BINARY(16) NOT NULL,
    aggregate_type VARCHAR(100) NOT NULL,
    aggregate_id BIGINT NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    payload LONGTEXT NOT NULL,
    status VARCHAR(20) NOT NULL,
    retry_count INT NOT NULL,
    next_retry_at DATETIME(6) NULL,
    published_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_chat_outbox_event_id UNIQUE (event_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS call_appointment (
    id BIGINT NOT NULL AUTO_INCREMENT,
    appointment_key BINARY(16) NOT NULL,
    chat_room_id BIGINT NOT NULL,
    proposer_id BIGINT NOT NULL,
    respondent_id BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL,
    scheduled_at DATETIME(6) NOT NULL,
    memo VARCHAR(500) NULL,
    cancel_reason VARCHAR(500) NULL,
    version BIGINT NOT NULL,
    responded_at DATETIME(6) NULL,
    canceled_at DATETIME(6) NULL,
    completed_at DATETIME(6) NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_call_appointment_key UNIQUE (appointment_key),
    CONSTRAINT fk_call_appointment_room FOREIGN KEY (chat_room_id) REFERENCES chat_room (id),
    CONSTRAINT fk_call_appointment_proposer FOREIGN KEY (proposer_id) REFERENCES user_account (user_id),
    CONSTRAINT fk_call_appointment_respondent FOREIGN KEY (respondent_id) REFERENCES user_account (user_id),
    INDEX ix_call_appointment_room_schedule (chat_room_id, scheduled_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS call_appointment_message (
    call_appointment_id BIGINT NOT NULL,
    chat_message_id BIGINT NOT NULL,
    appointment_event VARCHAR(20) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (call_appointment_id, chat_message_id),
    CONSTRAINT fk_call_appointment_message_appointment FOREIGN KEY (call_appointment_id) REFERENCES call_appointment (id),
    CONSTRAINT fk_call_appointment_message_message FOREIGN KEY (chat_message_id) REFERENCES chat_message (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS rtc_session (
    id BIGINT NOT NULL AUTO_INCREMENT,
    session_key BINARY(16) NOT NULL,
    call_appointment_id BIGINT NULL,
    chat_room_id BIGINT NOT NULL,
    listing_id BIGINT NOT NULL,
    transaction_id BIGINT NULL,
    seller_id BIGINT NOT NULL,
    buyer_id BIGINT NOT NULL,
    media_direction VARCHAR(30) NOT NULL,
    status VARCHAR(20) NOT NULL,
    connection_type VARCHAR(20) NULL,
    end_reason VARCHAR(30) NULL,
    expires_at DATETIME(6) NOT NULL,
    connected_at DATETIME(6) NULL,
    ended_at DATETIME(6) NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_rtc_session_key UNIQUE (session_key),
    CONSTRAINT fk_rtc_session_appointment FOREIGN KEY (call_appointment_id) REFERENCES call_appointment (id),
    CONSTRAINT fk_rtc_session_room FOREIGN KEY (chat_room_id) REFERENCES chat_room (id),
    CONSTRAINT fk_rtc_session_seller FOREIGN KEY (seller_id) REFERENCES user_account (user_id),
    CONSTRAINT fk_rtc_session_buyer FOREIGN KEY (buyer_id) REFERENCES user_account (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS rtc_session_summary (
    id BIGINT NOT NULL AUTO_INCREMENT,
    rtc_session_id BIGINT NOT NULL,
    connection_setup_ms BIGINT NULL,
    used_turn BIT(1) NOT NULL,
    avg_rtt_ms DECIMAL(12,3) NULL,
    max_rtt_ms DECIMAL(12,3) NULL,
    avg_packet_loss_rate DECIMAL(8,5) NULL,
    avg_jitter_ms DECIMAL(12,3) NULL,
    avg_inbound_bitrate_kbps DECIMAL(14,3) NULL,
    avg_fps DECIMAL(8,3) NULL,
    ice_restart_count INT NOT NULL,
    reconnect_count INT NOT NULL,
    recovery_succeeded BIT(1) NOT NULL,
    calculated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_rtc_session_summary_session UNIQUE (rtc_session_id),
    CONSTRAINT fk_rtc_session_summary_session FOREIGN KEY (rtc_session_id) REFERENCES rtc_session (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
