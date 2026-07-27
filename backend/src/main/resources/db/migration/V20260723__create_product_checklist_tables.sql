-- 2026-07-23 product 도메인(매물/카테고리/찜) + inspection 도메인(체크리스트/증거/재검수) 테이블
-- 근거: com.c203.limit.domain.product, com.c203.limit.domain.inspection 패키지 Entity
--
-- FK 정책: AGENTS.md의 "도메인 간 Entity를 직접 공유하지 않고 Service, ID, 이벤트 또는 명시적인
-- 인터페이스를 사용한다" 규칙에 따라, 같은 도메인 내부를 향하는 참조에만 FK를 걸고
-- 다른 도메인(product<->inspection, 회원, 채팅 등)을 향하는 참조는 컬럼만 두고 FK를 생략한다.
-- (reinspection_request_message는 아직 만들어지지 않은 채팅 연동 브릿지 테이블이라 이 마이그레이션 범위에서 제외한다.)

CREATE TABLE IF NOT EXISTS category (
    id BIGINT NOT NULL AUTO_INCREMENT,
    parent_id BIGINT NULL,
    name VARCHAR(100) NOT NULL,
    device_type VARCHAR(30) NOT NULL,
    manufacturer VARCHAR(50) NULL,
    os_family VARCHAR(30) NULL,
    model_code VARCHAR(50) NULL,
    display_order INT NOT NULL DEFAULT 0,
    is_active BIT(1) NOT NULL DEFAULT b'1',
    PRIMARY KEY (id),
    CONSTRAINT fk_category_parent FOREIGN KEY (parent_id) REFERENCES category (id),
    INDEX idx_category_parent (parent_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS listing (
    id BIGINT NOT NULL AUTO_INCREMENT,
    seller_id BIGINT NOT NULL,
    category_id BIGINT NOT NULL,
    title VARCHAR(200) NOT NULL,
    description TEXT NULL,
    price INT NOT NULL,
    checklist_template_id BIGINT NOT NULL,
    precheck_completed BIT(1) NOT NULL DEFAULT b'0',
    status VARCHAR(30) NOT NULL DEFAULT 'DRAFT',
    suspended_reason VARCHAR(200) NULL,
    buyer_id BIGINT NULL,
    reserved_at DATETIME(6) NULL,
    paid_at DATETIME(6) NULL,
    confirmed_at DATETIME(6) NULL,
    settled_at DATETIME(6) NULL,
    version BIGINT NOT NULL DEFAULT 0,
    deleted_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_listing_category FOREIGN KEY (category_id) REFERENCES category (id),
    INDEX idx_listing_category (category_id),
    INDEX idx_listing_seller (seller_id),
    INDEX idx_listing_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS listing_image (
    id BIGINT NOT NULL AUTO_INCREMENT,
    listing_id BIGINT NOT NULL,
    image_type VARCHAR(30) NOT NULL,
    s3_key VARCHAR(500) NOT NULL,
    cdn_url VARCHAR(500) NOT NULL,
    mime_type VARCHAR(50) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_listing_image_listing FOREIGN KEY (listing_id) REFERENCES listing (id),
    INDEX idx_listing_image_listing (listing_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS wishlist (
    wishlist_id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    listing_id BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (wishlist_id),
    CONSTRAINT fk_wishlist_listing FOREIGN KEY (listing_id) REFERENCES listing (id),
    CONSTRAINT uk_wishlist_user_listing UNIQUE (user_id, listing_id),
    INDEX idx_wishlist_listing (listing_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS listing_status_history (
    id BIGINT NOT NULL AUTO_INCREMENT,
    listing_id BIGINT NOT NULL,
    from_status VARCHAR(20) NOT NULL,
    to_status VARCHAR(20) NOT NULL,
    reason VARCHAR(200) NULL,
    actor_id BIGINT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_listing_status_history_listing FOREIGN KEY (listing_id) REFERENCES listing (id),
    INDEX idx_listing_status_history_listing (listing_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS checklist_template (
    id BIGINT NOT NULL AUTO_INCREMENT,
    category_id BIGINT NOT NULL,
    version INT NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'DRAFT',
    published_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    INDEX idx_checklist_template_category (category_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS checklist_template_item (
    id BIGINT NOT NULL AUTO_INCREMENT,
    checklist_template_id BIGINT NOT NULL,
    item_code VARCHAR(30) NOT NULL,
    name VARCHAR(100) NOT NULL,
    purpose VARCHAR(200) NOT NULL,
    capture_guide TEXT NOT NULL,
    evidence_type VARCHAR(30) NOT NULL,
    automation_type VARCHAR(30) NOT NULL DEFAULT 'NONE',
    parser_type VARCHAR(50) NULL,
    is_required BIT(1) NOT NULL DEFAULT b'1',
    allowed_formats VARCHAR(100) NULL,
    min_count INT NULL,
    max_count INT NULL,
    min_duration_sec INT NULL,
    max_duration_sec INT NULL,
    max_file_size_mb INT NULL,
    visible_to_buyer BIT(1) NOT NULL DEFAULT b'1',
    privacy_masking_required BIT(1) NOT NULL DEFAULT b'0',
    display_order INT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT fk_checklist_template_item_template
        FOREIGN KEY (checklist_template_id) REFERENCES checklist_template (id),
    INDEX idx_checklist_template_item_template (checklist_template_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS listing_checklist_item (
    id BIGINT NOT NULL AUTO_INCREMENT,
    listing_id BIGINT NOT NULL,
    template_item_id BIGINT NOT NULL,
    item_code VARCHAR(30) NOT NULL,
    name VARCHAR(100) NOT NULL,
    capture_guide TEXT NOT NULL,
    evidence_type VARCHAR(30) NOT NULL,
    automation_type VARCHAR(30) NOT NULL DEFAULT 'NONE',
    parser_type VARCHAR(50) NULL,
    is_required BIT(1) NOT NULL,
    allowed_formats VARCHAR(100) NULL,
    min_count INT NULL,
    max_count INT NULL,
    min_duration_sec INT NULL,
    max_duration_sec INT NULL,
    max_file_size_mb INT NULL,
    visible_to_buyer BIT(1) NOT NULL DEFAULT b'1',
    privacy_masking_required BIT(1) NOT NULL DEFAULT b'0',
    completion_status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    display_order INT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT fk_listing_checklist_item_template_item
        FOREIGN KEY (template_item_id) REFERENCES checklist_template_item (id),
    INDEX idx_listing_checklist_item_listing (listing_id),
    INDEX idx_listing_checklist_item_template_item (template_item_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS evidence (
    id BIGINT NOT NULL AUTO_INCREMENT,
    listing_id BIGINT NOT NULL,
    listing_checklist_item_id BIGINT NOT NULL,
    evidence_type VARCHAR(30) NOT NULL,
    s3_key VARCHAR(500) NOT NULL,
    cdn_url VARCHAR(500) NULL,
    mime_type VARCHAR(50) NOT NULL,
    captured_at DATETIME(6) NULL,
    uploaded_at DATETIME(6) NOT NULL,
    processing_status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    PRIMARY KEY (id),
    CONSTRAINT fk_evidence_listing_checklist_item
        FOREIGN KEY (listing_checklist_item_id) REFERENCES listing_checklist_item (id),
    INDEX idx_evidence_listing (listing_id),
    INDEX idx_evidence_listing_checklist_item (listing_checklist_item_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS reinspection_request (
    id BIGINT NOT NULL AUTO_INCREMENT,
    listing_id BIGINT NOT NULL,
    chat_room_id BIGINT NOT NULL,
    request_key CHAR(36) NOT NULL,
    reason VARCHAR(1000) NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'REQUESTED',
    version BIGINT NOT NULL DEFAULT 0,
    requested_at DATETIME(6) NOT NULL,
    completed_at DATETIME(6) NULL,
    canceled_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    buyer_id BIGINT NOT NULL,
    seller_id BIGINT NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_reinspection_request_request_key UNIQUE (request_key),
    INDEX idx_reinspection_request_listing (listing_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS reinspection_request_item (
    id BIGINT NOT NULL AUTO_INCREMENT,
    reinspection_request_id BIGINT NOT NULL,
    listing_checklist_item_id BIGINT NOT NULL,
    item_name_snapshot VARCHAR(100) NOT NULL,
    request_content VARCHAR(1000) NOT NULL,
    display_order INT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_reinspection_request_item_request
        FOREIGN KEY (reinspection_request_id) REFERENCES reinspection_request (id),
    CONSTRAINT fk_reinspection_request_item_checklist_item
        FOREIGN KEY (listing_checklist_item_id) REFERENCES listing_checklist_item (id),
    INDEX idx_reinspection_request_item_request (reinspection_request_id),
    INDEX idx_reinspection_request_item_checklist_item (listing_checklist_item_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
