-- =========================================================================
-- LIMIT 프로젝트 최종 전체 DB DDL (MySQL 8, InnoDB / utf8mb4)
--
-- 생성 기준:
--   1) backend/src/main/java/.../domain/*/entity, */agent 의 JPA 엔티티 = 컬럼/타입/연관관계의 진실
--   2) backend/src/main/resources/db/migration/*.sql (V1~V20260828, Flyway 버전 순서 기준)
--      = 실제 적용된 SQL 컨벤션(VARCHAR 길이, DEFAULT, 제약/인덱스 명명) 참고. 실제로 Docker MySQL 8에
--      전체 마이그레이션을 순서대로 재생(replay)한 뒤 스키마를 덤프해 구조 누락이 없는지 교차 검증함
--   3) 전체_ddl_최종.sql (2026-08 초 작성된 이전 버전, V20260825 기준) = 기반 문서, 그 이후 마이그레이션
--      (V20260826~V20260828)만 반영해 갱신함. 구조 불일치 시 1)·2)를 우선함
--
-- 이 프로젝트 실제 마이그레이션 컨벤션과 다른 점(참고용 구 DDL과 차이):
--   - enum은 네이티브 MySQL ENUM이 아니라 전부 VARCHAR(n)로 저장
--   - boolean은 BIT(1)
--   - 구 DDL에만 있고 현재 엔티티/마이그레이션에 없는 테이블(withdrawal_request, user_address,
--     inquiry, inquiry_answer, seller_application, login_attempt_log 등)은 아직 구현되지 않아 제외함
--
-- 컬럼 COMMENT는 이전 버전(전체_ddl_최종.sql)에 있던 문구를 그대로 재사용하고, 이전 버전 이후 추가된
-- 신규 테이블/컬럼(listing_report, listing_restoration_request, moderation_risk_signal,
-- listing.moderation_status, listing_image의 content_sha256/perceptual_hash/analyzed_at,
-- rtc_session.inspection_submitted_at)은 V20260826/V20260828 마이그레이션과 엔티티 Javadoc을
-- 바탕으로 새로 작성함.
--
-- 순환/교차 도메인 참조는 전부 파일 맨 끝 "교차 도메인 FK" 섹션에서 ALTER TABLE로 추가함
-- (그래야 위 5개 섹션의 CREATE TABLE 순서를 서로 신경 쓸 필요가 없음).
-- =========================================================================


-- =========================================================================
-- SECTION 1. 회원 / 관리자 / 인증
-- =========================================================================

CREATE TABLE IF NOT EXISTS user_account (
    user_id BIGINT NOT NULL AUTO_INCREMENT COMMENT '회원 고유 ID',
    email VARCHAR(255) NOT NULL COMMENT '로그인 이메일, 중복 불가',
    password VARCHAR(255) NULL COMMENT 'BCrypt 암호화 비밀번호, 소셜 전용 가입자는 NULL',
    nickname VARCHAR(50) NOT NULL COMMENT '회원 닉네임, 중복 불가',
    phone VARCHAR(20) NULL COMMENT '회원 연락처',
    profile_image_key VARCHAR(500) NULL COMMENT '프로필 이미지 S3 키',
    status VARCHAR(30) NOT NULL COMMENT '회원 상태',
    marketing_opt_in BIT(1) NOT NULL DEFAULT b'0' COMMENT '마케팅 정보 수신 동의 여부',
    email_verified_at DATETIME(6) NULL COMMENT '이메일 인증 완료 시각',
    last_login_at DATETIME(6) NULL COMMENT '마지막 로그인 성공 시각',
    password_changed_at DATETIME(6) NULL COMMENT '비밀번호 마지막 변경 시각',
    created_at DATETIME(6) NOT NULL COMMENT '가입 시각',
    updated_at DATETIME(6) NOT NULL COMMENT '정보 마지막 수정 시각',
    PRIMARY KEY (user_id),
    CONSTRAINT uk_user_account_email UNIQUE (email),
    CONSTRAINT uk_user_account_nickname UNIQUE (nickname),
    INDEX idx_user_account_status_created_at (status, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS admin_account (
    admin_id BIGINT NOT NULL AUTO_INCREMENT COMMENT '관리자 고유 ID',
    email VARCHAR(255) NOT NULL COMMENT '관리자 로그인 이메일',
    password VARCHAR(255) NOT NULL COMMENT 'BCrypt 암호화 관리자 비밀번호',
    name VARCHAR(255) NOT NULL COMMENT '관리자 이름',
    role VARCHAR(255) NOT NULL COMMENT '관리자 권한',
    status VARCHAR(255) NOT NULL COMMENT '관리자 계정 상태',
    last_login_at DATETIME(6) NULL COMMENT '마지막 로그인 성공 시각',
    password_changed_at DATETIME(6) NULL COMMENT '비밀번호 마지막 변경 시각',
    created_at DATETIME(6) NOT NULL COMMENT '계정 생성 시각',
    updated_at DATETIME(6) NOT NULL COMMENT '마지막 수정 시각',
    PRIMARY KEY (admin_id),
    CONSTRAINT uk_admin_account_email UNIQUE (email),
    INDEX idx_admin_account_status_role (status, role)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS social_account (
    social_account_id BIGINT NOT NULL AUTO_INCREMENT COMMENT '소셜 계정 연동 고유 ID',
    user_id BIGINT NOT NULL COMMENT '연동된 회원 ID',
    provider VARCHAR(20) NOT NULL COMMENT '소셜 로그인 공급자',
    provider_user_id VARCHAR(255) NOT NULL COMMENT '공급자 발급 고유 식별자',
    provider_email VARCHAR(255) NULL COMMENT '공급자가 반환한 이메일',
    linked_at DATETIME(6) NOT NULL COMMENT '최초 연동 시각',
    PRIMARY KEY (social_account_id),
    CONSTRAINT uk_social_provider_user UNIQUE (provider, provider_user_id),
    CONSTRAINT fk_social_account_user FOREIGN KEY (user_id) REFERENCES user_account (user_id),
    INDEX idx_social_account_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS member_terms_agreement (
    agreement_id BIGINT NOT NULL AUTO_INCREMENT COMMENT '약관 동의 이력 고유 ID',
    user_id BIGINT NOT NULL COMMENT '동의 회원 ID',
    terms_code VARCHAR(30) NOT NULL COMMENT '약관 코드',
    terms_version VARCHAR(30) NOT NULL COMMENT '약관 버전',
    is_agreed BIT(1) NOT NULL COMMENT '동의 여부',
    agreed_at DATETIME(6) NULL COMMENT '동의 시각',
    created_at DATETIME(6) NOT NULL COMMENT '생성 시각',
    PRIMARY KEY (agreement_id),
    CONSTRAINT uk_member_terms_version UNIQUE (user_id, terms_code, terms_version),
    CONSTRAINT fk_member_terms_user FOREIGN KEY (user_id) REFERENCES user_account (user_id),
    INDEX idx_member_terms_user_created_at (user_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS member_sanction (
    sanction_id BIGINT NOT NULL AUTO_INCREMENT COMMENT '제재 이력 고유 ID',
    user_id BIGINT NOT NULL COMMENT '제재 대상 회원 ID',
    restriction_type VARCHAR(255) NOT NULL COMMENT '제한 유형',
    reason_code VARCHAR(255) NOT NULL COMMENT '제재 사유 코드',
    reason_detail VARCHAR(500) NOT NULL COMMENT '제재 상세 사유',
    status VARCHAR(255) NOT NULL COMMENT '제재 상태',
    start_at DATETIME(6) NOT NULL COMMENT '제재 시작 시각',
    end_at DATETIME(6) NOT NULL COMMENT '제재 종료 예정 시각',
    admin_id BIGINT NOT NULL COMMENT '제재 등록 관리자 ID',
    released_at DATETIME(6) NULL COMMENT '수동 해제 시각',
    release_admin_id BIGINT NULL COMMENT '해제 관리자 ID',
    release_reason VARCHAR(500) NULL COMMENT '해제 사유',
    created_at DATETIME(6) NOT NULL COMMENT '등록 시각',
    PRIMARY KEY (sanction_id),
    CONSTRAINT fk_member_sanction_user FOREIGN KEY (user_id) REFERENCES user_account (user_id),
    INDEX idx_member_sanction_active (user_id, restriction_type, status),
    INDEX idx_member_sanction_period (start_at, end_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS admin_action_log (
    log_id BIGINT NOT NULL AUTO_INCREMENT COMMENT '감사 로그 고유 ID',
    admin_id BIGINT NOT NULL COMMENT '작업 수행 관리자 ID',
    action_type VARCHAR(255) NOT NULL COMMENT '작업 유형',
    target_type VARCHAR(255) NOT NULL COMMENT '작업 대상 자원 유형',
    target_id BIGINT NOT NULL COMMENT '대상 자원 ID (target_type에 따른 다형 참조, FK 없음)',
    reason VARCHAR(500) NULL COMMENT '작업 사유',
    before_data JSON NULL COMMENT '변경 전 데이터',
    after_data JSON NULL COMMENT '변경 후 데이터',
    ip_address VARCHAR(45) NULL COMMENT '요청 IP',
    created_at DATETIME(6) NOT NULL COMMENT '수행 시각',
    PRIMARY KEY (log_id),
    INDEX idx_admin_action_log_admin_created (admin_id, created_at),
    INDEX idx_admin_action_log_target (target_type, target_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


-- =========================================================================
-- SECTION 2. 상품 카탈로그 / 매물
-- =========================================================================

CREATE TABLE IF NOT EXISTS category (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '카테고리 고유 식별자',
    parent_id BIGINT NULL COMMENT '상위 카테고리 ID(자기참조). 최상위는 NULL',
    name VARCHAR(100) NOT NULL COMMENT '화면 노출명',
    device_type VARCHAR(30) NOT NULL COMMENT '기기 대분류',
    manufacturer VARCHAR(50) NULL COMMENT '제조사명(레거시 자유입력), 대분류는 NULL',
    manufacturer_id BIGINT NULL COMMENT '제조사 카탈로그 ID(신규 device_catalog 연동, nullable)',
    os_family VARCHAR(30) NULL COMMENT '운영체제 계열',
    model_code VARCHAR(50) NULL COMMENT '모델코드, 리프(모델)에만 채움',
    supported_storage_gb VARCHAR(100) NULL COMMENT '지원 저장 용량 목록(콤마 구분)',
    display_order INT NOT NULL DEFAULT 0 COMMENT '정렬 순서',
    is_active BIT(1) NOT NULL DEFAULT b'1' COMMENT '등록 폼 노출 여부',
    PRIMARY KEY (id),
    CONSTRAINT fk_category_parent FOREIGN KEY (parent_id) REFERENCES category (id),
    INDEX idx_category_parent (parent_id),
    INDEX idx_category_manufacturer (manufacturer_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS device_category (
    category_id BIGINT NOT NULL AUTO_INCREMENT COMMENT '기기 대분류 고유 식별자',
    code VARCHAR(30) NOT NULL COMMENT '카테고리 코드',
    name VARCHAR(100) NOT NULL COMMENT '카테고리명',
    display_order INT NOT NULL DEFAULT 0 COMMENT '정렬 순서',
    is_active BIT(1) NOT NULL DEFAULT b'1' COMMENT '노출 여부',
    PRIMARY KEY (category_id),
    CONSTRAINT uk_device_category_code UNIQUE (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS manufacturer (
    manufacturer_id BIGINT NOT NULL COMMENT '제조사 고유 식별자(수동 부여)',
    name VARCHAR(50) NOT NULL COMMENT '제조사명',
    normalized_name VARCHAR(50) NOT NULL COMMENT '정규화된 제조사명(중복 판별용)',
    is_active BIT(1) NOT NULL DEFAULT b'1' COMMENT '노출 여부',
    PRIMARY KEY (manufacturer_id),
    CONSTRAINT uk_manufacturer_normalized_name UNIQUE (normalized_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS device_model (
    model_id BIGINT NOT NULL AUTO_INCREMENT COMMENT '기기 모델 고유 식별자',
    category_id BIGINT NOT NULL COMMENT '소속 기기 대분류 ID',
    manufacturer_id BIGINT NULL COMMENT '제조사 ID',
    model_name VARCHAR(100) NOT NULL COMMENT '모델명',
    normalized_model_name VARCHAR(100) NOT NULL COMMENT '정규화된 모델명(검색/중복 판별용)',
    model_code VARCHAR(50) NOT NULL COMMENT '모델 코드',
    os_family VARCHAR(30) NULL COMMENT '운영체제 계열',
    release_year SMALLINT NULL COMMENT '출시 연도',
    is_active BIT(1) NOT NULL DEFAULT b'1' COMMENT '노출 여부',
    display_order INT NOT NULL DEFAULT 0 COMMENT '정렬 순서',
    review_status VARCHAR(30) NOT NULL DEFAULT 'VERIFIED' COMMENT '검수 상태(자동 등록/관리자 검수 대기 등)',
    source_type VARCHAR(30) NOT NULL DEFAULT 'CATALOG' COMMENT '등록 출처(카탈로그 시드/사용자 제보 등)',
    reported_by_member_id BIGINT NULL COMMENT '제보 회원 ID(사용자 제보로 생성된 경우)',
    reviewed_by_admin_id BIGINT NULL COMMENT '검수 관리자 ID',
    reviewed_at DATETIME(6) NULL COMMENT '검수 완료 시각',
    review_note VARCHAR(500) NULL COMMENT '검수 메모',
    disabled_at DATETIME(6) NULL COMMENT '비활성화 시각',
    disabled_by_admin_id BIGINT NULL COMMENT '비활성화 처리 관리자 ID',
    disable_reason VARCHAR(500) NULL COMMENT '비활성화 사유',
    replacement_model_id BIGINT NULL COMMENT '대체 모델 ID(병합/오등록 정정용)',
    created_at DATETIME(6) NOT NULL COMMENT '생성 시각',
    updated_at DATETIME(6) NOT NULL COMMENT '수정 시각',
    PRIMARY KEY (model_id),
    CONSTRAINT uk_device_model_manufacturer_code UNIQUE (manufacturer_id, model_code),
    CONSTRAINT fk_device_model_category FOREIGN KEY (category_id) REFERENCES device_category (category_id),
    CONSTRAINT fk_device_model_manufacturer FOREIGN KEY (manufacturer_id) REFERENCES manufacturer (manufacturer_id),
    CONSTRAINT fk_device_model_replacement FOREIGN KEY (replacement_model_id) REFERENCES device_model (model_id),
    INDEX idx_device_model_category_active (category_id, is_active),
    INDEX idx_device_model_normalized_name (normalized_model_name),
    INDEX idx_device_model_review_status_created (review_status, created_at),
    INDEX idx_device_model_category_active_updated (category_id, is_active, updated_at, model_id),
    INDEX idx_device_model_review_updated (review_status, updated_at, model_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS device_variant (
    variant_id BIGINT NOT NULL AUTO_INCREMENT COMMENT '판매 옵션(변형) 고유 식별자',
    model_id BIGINT NOT NULL COMMENT '소속 기기 모델 ID',
    variant_key VARCHAR(120) NOT NULL COMMENT '옵션 조합 키(중복 판별용)',
    display_name VARCHAR(150) NOT NULL COMMENT '옵션 표시명',
    color VARCHAR(50) NULL COMMENT '색상',
    storage_gb INT NULL COMMENT '저장 용량(GB)',
    memory_gb INT NULL COMMENT '메모리 용량(GB)',
    screen_size_inches DECIMAL(4,2) NULL COMMENT '화면 크기(인치)',
    cpu VARCHAR(100) NULL COMMENT 'CPU',
    gpu VARCHAR(100) NULL COMMENT 'GPU',
    weight_kg DECIMAL(5,3) NULL COMMENT '무게(kg)',
    connectivity VARCHAR(20) NULL COMMENT '통신 방식(Wi-Fi/Cellular 등)',
    is_active BIT(1) NOT NULL DEFAULT b'1' COMMENT '노출 여부',
    created_at DATETIME(6) NOT NULL COMMENT '생성 시각',
    updated_at DATETIME(6) NOT NULL COMMENT '수정 시각',
    PRIMARY KEY (variant_id),
    CONSTRAINT uk_device_variant_model_key UNIQUE (model_id, variant_key),
    CONSTRAINT fk_device_variant_model FOREIGN KEY (model_id) REFERENCES device_model (model_id),
    INDEX idx_device_variant_model_active (model_id, is_active)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS device_model_request (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '기기 모델 등록 요청 고유 식별자',
    requested_by_member_id BIGINT NOT NULL COMMENT '요청 회원 ID',
    parent_category_id BIGINT NOT NULL COMMENT '요청 대상 상위 카테고리 ID',
    manufacturer VARCHAR(50) NOT NULL COMMENT '제조사명(사용자 입력값)',
    model_name VARCHAR(100) NOT NULL COMMENT '모델명(사용자 입력값)',
    model_code VARCHAR(50) NULL COMMENT '모델 코드(사용자 입력값)',
    os_family VARCHAR(30) NOT NULL COMMENT '운영체제 계열',
    status VARCHAR(30) NOT NULL COMMENT '요청 처리 상태',
    resolved_category_id BIGINT NULL COMMENT '승인 후 매핑된 카테고리 ID',
    resolved_model_id BIGINT NULL COMMENT '승인 후 생성/매핑된 모델 ID',
    provisioned_at DATETIME(6) NULL COMMENT '모델 반영 완료 시각',
    reviewed_by_admin_id BIGINT NULL COMMENT '검수 관리자 ID',
    review_note VARCHAR(500) NULL COMMENT '검수 메모',
    created_at DATETIME(6) NOT NULL COMMENT '요청 시각',
    updated_at DATETIME(6) NOT NULL COMMENT '수정 시각',
    PRIMARY KEY (id),
    CONSTRAINT fk_device_model_request_category FOREIGN KEY (parent_category_id) REFERENCES category (id),
    CONSTRAINT fk_device_model_request_resolved_category FOREIGN KEY (resolved_category_id) REFERENCES category (id),
    CONSTRAINT fk_device_model_request_resolved_model FOREIGN KEY (resolved_model_id) REFERENCES device_model (model_id),
    INDEX idx_device_model_request_status_created (status, created_at),
    INDEX idx_device_model_request_requester (requested_by_member_id),
    INDEX idx_device_model_request_resolved_model (resolved_model_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS listing (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '매물 고유 식별자',
    seller_id BIGINT NOT NULL COMMENT '판매자 회원 ID',
    category_id BIGINT NOT NULL COMMENT '매물이 속한 리프(모델) 카테고리(레거시)',
    device_model_id BIGINT NULL COMMENT '기기 모델 카탈로그 ID(신규 연동, nullable)',
    device_variant_id BIGINT NULL COMMENT '기기 옵션 카탈로그 ID(신규 연동, nullable)',
    custom_manufacturer VARCHAR(50) NULL COMMENT '카탈로그에 없는 제조사 직접 입력값',
    custom_model_name VARCHAR(100) NULL COMMENT '카탈로그에 없는 모델명 직접 입력값',
    title VARCHAR(200) NOT NULL COMMENT '매물 제목',
    description TEXT NULL COMMENT '매물 상세 설명',
    price BIGINT NOT NULL COMMENT '판매 가격',
    color VARCHAR(50) NULL COMMENT '색상(옵션 스냅샷)',
    storage_gb INT NULL COMMENT '저장 용량(GB, 옵션 스냅샷)',
    screen_size_inches DECIMAL(4,2) NULL COMMENT '화면 크기(인치, 옵션 스냅샷)',
    memory_gb INT NULL COMMENT '메모리 용량(GB, 옵션 스냅샷)',
    connectivity VARCHAR(20) NULL COMMENT '통신 방식(옵션 스냅샷)',
    spec_snapshot JSON NULL COMMENT '등록 시점 사양 스냅샷(JSON, 카탈로그 변경과 무관하게 고정)',
    view_count BIGINT NOT NULL DEFAULT 0 COMMENT '조회수',
    trade_region VARCHAR(100) NULL COMMENT '거래 희망 지역',
    checklist_template_id BIGINT NOT NULL COMMENT '등록 시점 적용 템플릿(스냅샷 고정)',
    precheck_completed BIT(1) NOT NULL DEFAULT b'0' COMMENT '판매 전 계정 제거 안내 자가 체크(경고성)',
    draft_step INT NOT NULL DEFAULT 1 COMMENT '등록 진행 단계(임시저장 단계)',
    web_device_check_results JSON NULL COMMENT '웹 실동작 점검 결과 스냅샷(JSON)',
    status VARCHAR(30) NOT NULL DEFAULT 'DRAFT' COMMENT '매물 라이프사이클 상태',
    moderation_status VARCHAR(30) NOT NULL DEFAULT 'NORMAL' COMMENT '거래 라이프사이클과 분리된 운영 제재 상태(NORMAL/WARNING_ACK_REQUIRED/SUSPENDED/RESTORE_REQUESTED)',
    suspended_reason VARCHAR(200) NULL COMMENT '관리자 제재 사유',
    buyer_id BIGINT NULL COMMENT '거래 중 구매자',
    reserved_at DATETIME(6) NULL COMMENT '예약 시각',
    reserved_until DATETIME(6) NULL COMMENT '예약 만료 시각',
    paid_at DATETIME(6) NULL COMMENT '결제 승인 시각(에스크로)',
    handed_over_at DATETIME(6) NULL COMMENT '실물 인도 완료 시각',
    auto_confirm_at DATETIME(6) NULL COMMENT '자동 구매확정 예정 시각',
    confirmed_at DATETIME(6) NULL COMMENT '구매확정 시각',
    settled_at DATETIME(6) NULL COMMENT '정산 완료 시각',
    version BIGINT NOT NULL DEFAULT 0 COMMENT '낙관적 잠금(동시 예약 방지)',
    deleted_at DATETIME(6) NULL COMMENT '논리 삭제 시각. SETTLED 매물은 삭제 금지',
    created_at DATETIME(6) NOT NULL COMMENT '등록 시각',
    updated_at DATETIME(6) NOT NULL COMMENT '마지막 수정 시각',
    PRIMARY KEY (id),
    CONSTRAINT fk_listing_category FOREIGN KEY (category_id) REFERENCES category (id),
    CONSTRAINT fk_listing_device_model FOREIGN KEY (device_model_id) REFERENCES device_model (model_id),
    CONSTRAINT fk_listing_device_variant FOREIGN KEY (device_variant_id) REFERENCES device_variant (variant_id),
    INDEX idx_listing_category (category_id),
    INDEX idx_listing_seller (seller_id),
    INDEX idx_listing_status (status),
    INDEX idx_listing_public_feed (status, deleted_at, created_at),
    INDEX idx_listing_seller_feed (seller_id, status, deleted_at, updated_at),
    INDEX idx_listing_seller_all_feed (seller_id, deleted_at, updated_at),
    INDEX idx_listing_view_count_feed (status, deleted_at, view_count DESC),
    INDEX idx_listing_model_updated (device_model_id, updated_at, id),
    INDEX idx_listing_model_created (device_model_id, created_at, id),
    INDEX idx_listing_public_moderation (status, moderation_status, deleted_at, created_at),
    INDEX idx_listing_seller_moderation (seller_id, moderation_status, updated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS listing_image (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '이미지 고유 식별자',
    listing_id BIGINT NOT NULL COMMENT '소속 매물 ID',
    image_type VARCHAR(30) NOT NULL COMMENT '대표/상세 이미지 구분',
    display_order INT NOT NULL DEFAULT 0 COMMENT '정렬(노출) 순서',
    s3_key VARCHAR(500) NOT NULL COMMENT 'S3 원본 경로(내부용)',
    cdn_url VARCHAR(500) NULL COMMENT 'CDN 접근 URL',
    mime_type VARCHAR(50) NOT NULL COMMENT 'MIME 타입',
    content_sha256 CHAR(64) NULL COMMENT '이미지 원본 바이트의 SHA-256 해시(완전 동일 이미지 중복 탐지용)',
    perceptual_hash CHAR(16) NULL COMMENT '지각적 해시(pHash, 유사 이미지 탐지용)',
    analyzed_at DATETIME(6) NULL COMMENT '이미지 해시 분석 완료 시각',
    created_at DATETIME(6) NOT NULL COMMENT '등록일시',
    PRIMARY KEY (id),
    CONSTRAINT uk_listing_image_s3_key UNIQUE (s3_key),
    CONSTRAINT fk_listing_image_listing FOREIGN KEY (listing_id) REFERENCES listing (id),
    INDEX idx_listing_image_listing (listing_id),
    INDEX idx_listing_image_thumbnail (listing_id, image_type, id),
    INDEX idx_listing_image_order (listing_id, display_order),
    INDEX idx_listing_image_content_hash (content_sha256),
    INDEX idx_listing_image_perceptual_hash (perceptual_hash)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS listing_status_history (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '이력 고유 식별자',
    listing_id BIGINT NOT NULL COMMENT '대상 매물 ID',
    from_status VARCHAR(20) NOT NULL COMMENT '이전 상태',
    to_status VARCHAR(20) NOT NULL COMMENT '변경 상태',
    reason VARCHAR(200) NULL COMMENT '사유',
    actor_id BIGINT NULL COMMENT '수행자 ID (회원 또는 관리자, 다형 참조라 FK 없음. 시스템 처리 시 NULL)',
    created_at DATETIME(6) NOT NULL COMMENT '변경 시각',
    PRIMARY KEY (id),
    CONSTRAINT fk_listing_status_history_listing FOREIGN KEY (listing_id) REFERENCES listing (id),
    INDEX idx_listing_status_history_listing (listing_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS listing_report (
    report_id BIGINT NOT NULL AUTO_INCREMENT COMMENT '신고 고유 ID',
    listing_id BIGINT NOT NULL COMMENT '신고 대상 매물 ID',
    reporter_id BIGINT NOT NULL COMMENT '신고한 회원 ID',
    category VARCHAR(40) NOT NULL COMMENT '신고 사유 분류(정보 불일치/중복 등록/사기 의심/금지 품목/부적절한 콘텐츠/기타)',
    detail VARCHAR(1000) NOT NULL COMMENT '신고 상세 내용',
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING' COMMENT '처리 상태(대기/기각/경고 발부/정지/처리 완료)',
    reviewer_admin_id BIGINT NULL COMMENT '처리한 관리자 ID',
    admin_note VARCHAR(1000) NULL COMMENT '관리자 처리 메모',
    reviewed_at DATETIME(6) NULL COMMENT '관리자 처리 시각',
    acknowledged_at DATETIME(6) NULL COMMENT '경고 대상 판매자가 경고 내용을 확인한 시각',
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '신고 접수 시각',
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '마지막 수정 시각',
    PRIMARY KEY (report_id),
    CONSTRAINT fk_listing_report_listing FOREIGN KEY (listing_id) REFERENCES listing (id),
    CONSTRAINT uk_listing_report_reporter UNIQUE (listing_id, reporter_id),
    INDEX idx_listing_report_status_created (status, created_at),
    INDEX idx_listing_report_listing_status (listing_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS listing_restoration_request (
    restoration_request_id BIGINT NOT NULL AUTO_INCREMENT COMMENT '매물 복구 요청 고유 ID',
    listing_id BIGINT NOT NULL COMMENT '복구 대상 매물 ID',
    seller_id BIGINT NOT NULL COMMENT '요청한 판매자 회원 ID',
    request_note VARCHAR(1000) NULL COMMENT '판매자 소명 내용',
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING' COMMENT '처리 상태(대기/승인/반려)',
    reviewer_admin_id BIGINT NULL COMMENT '처리한 관리자 ID',
    review_note VARCHAR(1000) NULL COMMENT '관리자 심사 메모',
    reviewed_at DATETIME(6) NULL COMMENT '관리자 처리 시각',
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '요청 시각',
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '마지막 수정 시각',
    PRIMARY KEY (restoration_request_id),
    CONSTRAINT fk_listing_restoration_listing FOREIGN KEY (listing_id) REFERENCES listing (id),
    INDEX idx_listing_restoration_status_created (status, created_at),
    INDEX idx_listing_restoration_listing_status (listing_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS moderation_risk_signal (
    risk_signal_id BIGINT NOT NULL AUTO_INCREMENT COMMENT '위험 신호 고유 ID',
    seller_id BIGINT NOT NULL COMMENT '대상 판매자 회원 ID',
    listing_id BIGINT NULL COMMENT '직접 관련된 매물 ID',
    related_listing_id BIGINT NULL COMMENT '비교 대상이 된 다른 매물 ID(유사 매물 탐지 시)',
    signal_type VARCHAR(40) NOT NULL COMMENT '위험 신호 유형(단시간 다량 등록/유사 제목/유사 이미지)',
    score INT NOT NULL COMMENT '위험도 점수(0~100)',
    detail VARCHAR(500) NOT NULL COMMENT '탐지 상세 내용',
    fingerprint VARCHAR(180) NOT NULL COMMENT '동일 신호 중복 적재 방지용 지문 값',
    status VARCHAR(20) NOT NULL DEFAULT 'OPEN' COMMENT '처리 상태(미해결/해결됨)',
    resolved_by_admin_id BIGINT NULL COMMENT '해결 처리한 관리자 ID',
    resolution_note VARCHAR(500) NULL COMMENT '관리자 해결 메모',
    resolved_at DATETIME(6) NULL COMMENT '해결 처리 시각',
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '탐지(생성) 시각',
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '마지막 수정 시각',
    PRIMARY KEY (risk_signal_id),
    CONSTRAINT fk_moderation_risk_listing FOREIGN KEY (listing_id) REFERENCES listing (id),
    CONSTRAINT fk_moderation_risk_related_listing FOREIGN KEY (related_listing_id) REFERENCES listing (id),
    CONSTRAINT uk_moderation_risk_fingerprint UNIQUE (fingerprint),
    INDEX idx_moderation_risk_status_created (status, created_at),
    INDEX idx_moderation_risk_seller_status (seller_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS wishlist (
    wishlist_id BIGINT NOT NULL AUTO_INCREMENT COMMENT '관심상품 고유 ID',
    user_id BIGINT NOT NULL COMMENT '등록 회원 ID',
    listing_id BIGINT NOT NULL COMMENT '관심 매물 ID',
    created_at DATETIME(6) NOT NULL COMMENT '등록일시',
    PRIMARY KEY (wishlist_id),
    CONSTRAINT uk_wishlist_user_listing UNIQUE (user_id, listing_id),
    CONSTRAINT fk_wishlist_listing FOREIGN KEY (listing_id) REFERENCES listing (id),
    INDEX idx_wishlist_listing (listing_id),
    INDEX idx_wishlist_user_created (user_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS media_upload_session (
    upload_id CHAR(36) NOT NULL COMMENT '업로드 세션 고유 식별자(UUID)',
    uploader_id BIGINT NOT NULL COMMENT '업로드 요청 회원 ID',
    listing_id BIGINT NOT NULL COMMENT '대상 매물 ID',
    listing_checklist_item_id BIGINT NULL COMMENT '대상 체크리스트 항목 ID(증빙 업로드인 경우)',
    purpose VARCHAR(30) NOT NULL COMMENT '업로드 목적(대표 이미지/증빙 등)',
    bucket_name VARCHAR(255) NOT NULL COMMENT 'S3 버킷명',
    object_key VARCHAR(500) NOT NULL COMMENT '업로드 임시 오브젝트 키',
    final_object_key VARCHAR(500) NOT NULL COMMENT '업로드 완료 후 최종 오브젝트 키',
    original_filename VARCHAR(255) NOT NULL COMMENT '원본 파일명',
    expected_mime_type VARCHAR(100) NOT NULL COMMENT '기대 MIME 타입',
    expected_file_size BIGINT NOT NULL COMMENT '기대 파일 크기(byte)',
    expected_duration_seconds INT NULL COMMENT '기대 영상 길이(초)',
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING' COMMENT '업로드 세션 상태',
    expires_at DATETIME(6) NOT NULL COMMENT '세션 만료 시각',
    completed_at DATETIME(6) NULL COMMENT '업로드 완료 시각',
    created_at DATETIME(6) NOT NULL COMMENT '세션 생성 시각',
    version BIGINT NOT NULL DEFAULT 0 COMMENT '동시성 제어용 낙관적 락 버전',
    PRIMARY KEY (upload_id),
    CONSTRAINT uk_media_upload_session_object UNIQUE (bucket_name, object_key),
    CONSTRAINT uk_media_upload_session_final_object UNIQUE (bucket_name, final_object_key),
    INDEX idx_media_upload_session_listing (listing_id),
    INDEX idx_media_upload_session_expiration (status, expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


-- =========================================================================
-- SECTION 3. 판매자 / 결제 / 환불 / 정산
-- =========================================================================

CREATE TABLE IF NOT EXISTS seller (
    seller_id BIGINT NOT NULL AUTO_INCREMENT COMMENT '판매자 고유 ID',
    user_id BIGINT NOT NULL COMMENT '연동된 회원 ID',
    seller_type VARCHAR(20) NOT NULL COMMENT '셀러 유형(개인/기업 등)',
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' COMMENT '판매자 상태',
    country_code VARCHAR(2) NOT NULL COMMENT '셀러 소재 국가 코드(ISO 3166-1 alpha-2)',
    business_name VARCHAR(100) NULL COMMENT '상호명(기업 셀러)',
    settlement_bank_name VARCHAR(100) NOT NULL COMMENT '정산 은행명',
    settlement_account_holder VARCHAR(100) NOT NULL COMMENT '정산 계좌 예금주',
    settlement_account_last4 VARCHAR(4) NOT NULL COMMENT '정산 계좌 뒷 4자리(마스킹 저장)',
    created_at DATETIME(6) NOT NULL COMMENT '생성일시',
    updated_at DATETIME(6) NOT NULL COMMENT '수정일시',
    PRIMARY KEY (seller_id),
    CONSTRAINT uk_seller_user UNIQUE (user_id),
    INDEX idx_seller_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS payment (
    payment_id BIGINT NOT NULL AUTO_INCREMENT COMMENT '고유 식별자',
    listing_id BIGINT NOT NULL COMMENT '대상 매물 ID',
    buyer_id BIGINT NOT NULL COMMENT '결제 회원(구매자) ID',
    payment_version INT NOT NULL COMMENT '낙관적 락(동시 결제 방지)',
    idempotency_key VARCHAR(100) NOT NULL COMMENT '멱등키',
    attempt_no INT NOT NULL COMMENT '동일 거래 재시도 횟수',
    provider_order_id VARCHAR(64) NULL COMMENT 'PG사 주문 ID',
    pg_provider VARCHAR(20) NOT NULL COMMENT 'PG사',
    method VARCHAR(20) NULL COMMENT '결제수단',
    provider_transaction_id VARCHAR(200) NULL COMMENT 'PG사 거래 ID',
    pg_event_id VARCHAR(200) NULL COMMENT 'PG 웹훅 이벤트 ID(중복 처리 방지)',
    webhook_verified BIT(1) NOT NULL COMMENT 'PG 웹훅 서명 검증 여부',
    requested_amount DECIMAL(12,2) NOT NULL COMMENT '요청 금액',
    approved_amount DECIMAL(12,2) NULL COMMENT '승인 금액',
    status VARCHAR(30) NOT NULL COMMENT '결제 상태',
    failed_reason VARCHAR(255) NULL COMMENT '실패 사유',
    requested_at DATETIME(6) NOT NULL COMMENT '요청 시각',
    approved_at DATETIME(6) NULL COMMENT '승인 시각',
    confirm_attempted_at DATETIME(6) NULL COMMENT '결제 승인 시도 시각',
    created_at DATETIME(6) NOT NULL COMMENT '생성 시각',
    updated_at DATETIME(6) NOT NULL COMMENT '수정 시각',
    PRIMARY KEY (payment_id),
    CONSTRAINT uk_payment_idempotency_key UNIQUE (idempotency_key),
    CONSTRAINT uk_payment_pg_event_id UNIQUE (pg_event_id),
    CONSTRAINT uk_payment_provider_order_id UNIQUE (provider_order_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS refund_request (
    refund_request_id BIGINT NOT NULL AUTO_INCREMENT COMMENT '환불 요청 고유 식별자',
    payment_id BIGINT NOT NULL COMMENT '대상 결제 ID',
    reason VARCHAR(500) NOT NULL COMMENT '환불 사유',
    checklist_item_id BIGINT NULL COMMENT '환불 사유가 된 체크리스트 항목 ID(선택)',
    refund_idempotency_key VARCHAR(100) NULL COMMENT '환불 요청 멱등키',
    retry_count INT NOT NULL DEFAULT 0 COMMENT 'PG 환불 재시도 횟수',
    next_retry_at DATETIME(6) NULL COMMENT '다음 재시도 예정 시각',
    status VARCHAR(30) NOT NULL COMMENT '환불 처리 상태',
    reject_reason VARCHAR(500) NULL COMMENT '거절 사유',
    requested_at DATETIME(6) NOT NULL COMMENT '요청 시각',
    processed_at DATETIME(6) NULL COMMENT '처리 완료 시각',
    processed_admin_id BIGINT NULL COMMENT '처리 관리자 ID',
    pg_refund_transaction_id VARCHAR(200) NULL COMMENT 'PG사 환불 거래 ID',
    refunded_at DATETIME(6) NULL COMMENT '환불 완료 시각',
    PRIMARY KEY (refund_request_id),
    CONSTRAINT fk_refund_request_payment FOREIGN KEY (payment_id) REFERENCES payment (payment_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS settlement (
    settlement_id BIGINT NOT NULL AUTO_INCREMENT COMMENT '고유 식별자',
    listing_id BIGINT NOT NULL COMMENT '대상 매물 ID',
    seller_id BIGINT NOT NULL COMMENT '정산 대상 판매자 ID',
    amount INT NOT NULL COMMENT '정산 금액',
    status VARCHAR(30) NOT NULL COMMENT '정산 상태',
    pending_at DATETIME(6) NOT NULL COMMENT '보류 시작 시각',
    settled_at DATETIME(6) NULL COMMENT '정산 완료 시각',
    canceled_at DATETIME(6) NULL COMMENT '정산 취소 시각',
    PRIMARY KEY (settlement_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


-- =========================================================================
-- SECTION 4. 검수 / 체크리스트 / 증빙
-- =========================================================================

CREATE TABLE IF NOT EXISTS checklist_template (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '템플릿 고유 식별자',
    category_id BIGINT NOT NULL COMMENT '리프(모델) 카테고리 ID',
    version INT NOT NULL COMMENT '템플릿 버전. 수정 = 새 버전 발행',
    status VARCHAR(30) NOT NULL DEFAULT 'DRAFT' COMMENT 'PUBLISHED 후 항목 수정 금지',
    published_at DATETIME(6) NULL COMMENT '발행 시각',
    created_at DATETIME(6) NOT NULL COMMENT '생성일시',
    PRIMARY KEY (id),
    INDEX idx_checklist_template_category (category_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS checklist_template_item (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '템플릿 항목 고유 식별자',
    checklist_template_id BIGINT NOT NULL COMMENT '소속 템플릿 ID',
    item_code VARCHAR(30) NOT NULL COMMENT '항목 코드(예: SP-EXT-001)',
    name VARCHAR(100) NOT NULL COMMENT '항목명',
    purpose VARCHAR(200) NOT NULL COMMENT '확인 목적',
    capture_guide TEXT NOT NULL COMMENT '판매자 촬영 안내',
    evidence_type VARCHAR(30) NOT NULL COMMENT '증거 유형',
    automation_type VARCHAR(30) NOT NULL DEFAULT 'NONE' COMMENT '검수 자동화 유형',
    parser_type VARCHAR(50) NULL COMMENT 'battery_report, dxdiag 등. 없으면 NULL',
    is_required BIT(1) NOT NULL DEFAULT b'1' COMMENT '필수 여부. 필수 미완료 시 판매 게시 불가',
    allowed_formats VARCHAR(100) NULL COMMENT '허용 파일 형식(콤마 구분)',
    min_count INT NULL COMMENT '사진 최소 개수',
    max_count INT NULL COMMENT '사진 최대 개수',
    min_duration_sec INT NULL COMMENT '영상 최소 길이(초)',
    max_duration_sec INT NULL COMMENT '영상 최대 길이(초)',
    max_file_size_mb INT NULL COMMENT '파일 최대 용량(MB)',
    visible_to_buyer BIT(1) NOT NULL DEFAULT b'1' COMMENT '구매자 공개 여부',
    privacy_masking_required BIT(1) NOT NULL DEFAULT b'0' COMMENT '개인정보 마스킹 필요 여부',
    display_order INT NOT NULL DEFAULT 0 COMMENT '정렬 순서',
    PRIMARY KEY (id),
    CONSTRAINT uk_checklist_template_item_template_item_code UNIQUE (checklist_template_id, item_code),
    CONSTRAINT fk_checklist_template_item_template
        FOREIGN KEY (checklist_template_id) REFERENCES checklist_template (id),
    INDEX idx_checklist_template_item_template (checklist_template_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS listing_checklist_item (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '스냅샷 항목 고유 식별자',
    listing_id BIGINT NOT NULL COMMENT '소속 매물 ID',
    template_item_id BIGINT NOT NULL COMMENT '원본 템플릿 항목 ID',
    item_code VARCHAR(30) NOT NULL COMMENT '항목 코드(스냅샷 복사)',
    item_origin VARCHAR(30) NOT NULL DEFAULT 'BASE' COMMENT '항목 출처(BASE=기본 항목, CONFIRMED_FEATURE=판매자가 선택한 기능 항목). 등록 이후 템플릿이 개정돼도 바뀌지 않는 생성 시점 스냅샷',
    feature_code VARCHAR(30) NULL COMMENT 'item_origin이 CONFIRMED_FEATURE일 때만 값이 있음',
    name VARCHAR(100) NOT NULL COMMENT '항목명(스냅샷 복사)',
    capture_guide TEXT NOT NULL COMMENT '촬영 안내(스냅샷 복사)',
    evidence_type VARCHAR(30) NOT NULL COMMENT '증거 유형',
    automation_type VARCHAR(30) NOT NULL DEFAULT 'NONE' COMMENT '검수 자동화 유형(스냅샷 복사)',
    parser_type VARCHAR(50) NULL COMMENT 'battery_report, dxdiag 등(스냅샷 복사)',
    is_required BIT(1) NOT NULL COMMENT '필수 여부',
    allowed_formats VARCHAR(100) NULL COMMENT '허용 파일 형식',
    min_count INT NULL COMMENT '사진 최소 개수',
    max_count INT NULL COMMENT '사진 최대 개수',
    min_duration_sec INT NULL COMMENT '영상 최소 길이(초)',
    max_duration_sec INT NULL COMMENT '영상 최대 길이(초)',
    max_file_size_mb INT NULL COMMENT '파일 최대 용량(MB)',
    visible_to_buyer BIT(1) NOT NULL DEFAULT b'1' COMMENT '구매자 공개 여부',
    privacy_masking_required BIT(1) NOT NULL DEFAULT b'0' COMMENT '개인정보 마스킹 필요 여부',
    completion_status VARCHAR(30) NOT NULL DEFAULT 'PENDING' COMMENT '항목 완료 상태',
    device_check_result VARCHAR(20) NULL COMMENT '웹 실동작 점검(키보드/포인터 등) 결과',
    display_order INT NOT NULL DEFAULT 0 COMMENT '정렬 순서',
    manual_model_name VARCHAR(100) NULL COMMENT '자동 파싱 실패 시 판매자가 직접 입력한 모델명',
    manual_storage_capacity VARCHAR(30) NULL COMMENT '자동 파싱 실패 시 판매자가 직접 입력한 저장 용량',
    manual_os_version VARCHAR(200) NULL COMMENT '자동 파싱 실패 시 판매자가 직접 입력한 OS 버전',
    manual_cpu VARCHAR(100) NULL COMMENT '자동 파싱 실패 시 판매자가 직접 입력한 CPU 정보',
    PRIMARY KEY (id),
    CONSTRAINT uk_listing_checklist_item_listing_item_code UNIQUE (listing_id, item_code),
    CONSTRAINT fk_listing_checklist_item_template_item
        FOREIGN KEY (template_item_id) REFERENCES checklist_template_item (id),
    INDEX idx_listing_checklist_item_listing (listing_id),
    INDEX idx_listing_checklist_item_template_item (template_item_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS evidence (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '증거 고유 식별자',
    listing_id BIGINT NOT NULL COMMENT '소속 매물 ID',
    listing_checklist_item_id BIGINT NOT NULL COMMENT '대상 스냅샷 항목 ID',
    evidence_type VARCHAR(30) NOT NULL COMMENT '증거 유형',
    s3_key VARCHAR(500) NOT NULL COMMENT 'S3 원본 경로',
    cdn_url VARCHAR(500) NULL COMMENT 'CDN 접근 URL(처리 완료 후)',
    mime_type VARCHAR(50) NOT NULL COMMENT 'MIME 타입',
    captured_at DATETIME(6) NULL COMMENT '촬영 시각',
    uploaded_at DATETIME(6) NOT NULL COMMENT '업로드 완료 시각',
    processing_status VARCHAR(30) NOT NULL DEFAULT 'PENDING' COMMENT '미디어 처리 상태',
    PRIMARY KEY (id),
    CONSTRAINT fk_evidence_listing_checklist_item
        FOREIGN KEY (listing_checklist_item_id) REFERENCES listing_checklist_item (id),
    INDEX idx_evidence_listing (listing_id),
    INDEX idx_evidence_listing_checklist_item (listing_checklist_item_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS account_removal_guide (
    account_removal_guide_id BIGINT NOT NULL AUTO_INCREMENT COMMENT '고유 식별자',
    device_type VARCHAR(255) NOT NULL COMMENT '적용 기기 대분류 (category.device_type 재사용)',
    manufacturer VARCHAR(50) NOT NULL COMMENT '제조사',
    template_version INT NOT NULL COMMENT '가이드 버전',
    steps JSON NOT NULL COMMENT '단계별 가이드 콘텐츠',
    disclaimer_text LONGTEXT NOT NULL COMMENT '고지 문구',
    created_at DATETIME(6) NOT NULL COMMENT '생성일시',
    PRIMARY KEY (account_removal_guide_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS listing_account_removal_check (
    listing_account_removal_check_id BIGINT NOT NULL AUTO_INCREMENT COMMENT '고유 식별자',
    listing_id BIGINT NOT NULL COMMENT '대상 매물 ID',
    guide_id BIGINT NOT NULL COMMENT '등록 시점 가이드 버전 고정',
    seller_confirmed BIT(1) NOT NULL COMMENT '판매자 자가체크 완료 여부',
    confirmed_at DATETIME(6) NULL COMMENT '체크 완료 시각',
    screenshot_url VARCHAR(500) NULL COMMENT '선택 업로드 스크린샷',
    PRIMARY KEY (listing_account_removal_check_id),
    CONSTRAINT fk_listing_account_removal_check_guide
        FOREIGN KEY (guide_id) REFERENCES account_removal_guide (account_removal_guide_id),
    INDEX idx_listing_account_removal_check_listing (listing_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS battery_report_result (
    battery_report_result_id BIGINT NOT NULL AUTO_INCREMENT COMMENT '고유 식별자',
    evidence_id BIGINT NOT NULL COMMENT '대상 증거 ID (evidence_type=DIAGNOSTIC_FILE)',
    design_capacity VARCHAR(30) NULL COMMENT '설계 용량',
    full_charge_capacity VARCHAR(30) NULL COMMENT '완전충전 용량',
    cycle_count INT NULL COMMENT '충전 사이클 수',
    battery_manufacturer VARCHAR(50) NULL COMMENT '배터리 제조사',
    capacity_ratio DECIMAL(5,2) NULL COMMENT '완전충전용량/설계용량 * 100',
    parser_version VARCHAR(20) NOT NULL COMMENT '파서 버전',
    parse_status VARCHAR(255) NOT NULL COMMENT '파싱 결과 상태',
    parsed_at DATETIME(6) NOT NULL COMMENT '파싱 시각',
    PRIMARY KEY (battery_report_result_id),
    CONSTRAINT fk_battery_report_result_evidence
        FOREIGN KEY (evidence_id) REFERENCES evidence (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS dxdiag_result (
    dxdiag_result_id BIGINT NOT NULL AUTO_INCREMENT COMMENT '고유 식별자',
    evidence_id BIGINT NOT NULL COMMENT '대상 증거 ID (evidence_type=DIAGNOSTIC_FILE)',
    model_name VARCHAR(100) NULL COMMENT '기기 모델명(dxdiag 파싱)',
    os_version VARCHAR(200) NULL COMMENT '운영체제 버전(dxdiag 파싱)',
    storage_capacity VARCHAR(30) NULL COMMENT '저장 용량(dxdiag 파싱)',
    cpu VARCHAR(100) NULL COMMENT 'CPU 정보',
    memory VARCHAR(50) NULL COMMENT '메모리 정보',
    gpu VARCHAR(100) NULL COMMENT 'GPU 정보',
    gpu_memory VARCHAR(30) NULL COMMENT 'GPU 메모리',
    driver_version VARCHAR(50) NULL COMMENT '드라이버 버전',
    sound_device VARCHAR(100) NULL COMMENT '사운드 장치',
    parser_version VARCHAR(20) NOT NULL COMMENT '파서 버전',
    parse_status VARCHAR(255) NOT NULL COMMENT '파싱 결과 상태',
    parsed_at DATETIME(6) NOT NULL COMMENT '파싱 시각',
    PRIMARY KEY (dxdiag_result_id),
    CONSTRAINT fk_dxdiag_result_evidence
        FOREIGN KEY (evidence_id) REFERENCES evidence (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS ocr_result (
    ocr_result_id BIGINT NOT NULL AUTO_INCREMENT COMMENT '고유 식별자',
    evidence_id BIGINT NOT NULL COMMENT '대상 증거 ID (evidence_type=VIDEO, 종합진단 화면)',
    field_type VARCHAR(255) NOT NULL COMMENT '인식 필드 종류',
    raw_text LONGTEXT NULL COMMENT '원본 인식 텍스트',
    parsed_value VARCHAR(200) NULL COMMENT '정규화된 값',
    confidence DECIMAL(4,3) NULL COMMENT '확신도 (0.000~1.000)',
    ocr_model_version VARCHAR(30) NOT NULL COMMENT 'OCR 모델/엔진 버전',
    detected_at DATETIME(6) NOT NULL COMMENT '인식 시각',
    PRIMARY KEY (ocr_result_id),
    CONSTRAINT fk_ocr_result_evidence
        FOREIGN KEY (evidence_id) REFERENCES evidence (id),
    INDEX idx_ocr_result_evidence (evidence_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS reinspection_request (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '내부 식별자',
    listing_id BIGINT NOT NULL COMMENT '재검수 대상 매물 ID',
    chat_room_id BIGINT NOT NULL COMMENT '알림을 전송할 채팅방 ID',
    request_key CHAR(36) NOT NULL COMMENT '외부 노출용 UUID',
    reason VARCHAR(1000) NOT NULL COMMENT '구매자가 작성한 전체 요청 사유',
    status VARCHAR(30) NOT NULL DEFAULT 'REQUESTED' COMMENT '재검수 진행 상태',
    version BIGINT NOT NULL DEFAULT 0 COMMENT '완료·취소 동시 요청 방지용 낙관적 잠금 버전',
    requested_at DATETIME(6) NOT NULL COMMENT '요청 시각',
    completed_at DATETIME(6) NULL COMMENT '판매자 완료 시각',
    canceled_at DATETIME(6) NULL COMMENT '취소 시각',
    created_at DATETIME(6) NOT NULL COMMENT '생성 시각',
    updated_at DATETIME(6) NOT NULL COMMENT '수정 시각',
    buyer_id BIGINT NOT NULL COMMENT '재검수를 요청한 구매자 ID',
    seller_id BIGINT NOT NULL COMMENT '재검수를 수행할 판매자 ID',
    PRIMARY KEY (id),
    CONSTRAINT uk_reinspection_request_request_key UNIQUE (request_key),
    INDEX idx_reinspection_request_listing (listing_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS reinspection_request_item (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '내부 식별자',
    reinspection_request_id BIGINT NOT NULL COMMENT '재검수 요청 ID',
    listing_checklist_item_id BIGINT NOT NULL COMMENT '구매자가 선택한 체크리스트 항목 ID',
    item_name_snapshot VARCHAR(100) NOT NULL COMMENT '채팅 이력 보존용 항목명 스냅샷',
    request_content VARCHAR(1000) NOT NULL COMMENT '해당 항목에 대한 구매자 요청 내용',
    display_order INT NOT NULL DEFAULT 0 COMMENT '채팅 표시 순서',
    created_at DATETIME(6) NOT NULL COMMENT '생성 시각',
    PRIMARY KEY (id),
    CONSTRAINT fk_reinspection_request_item_request
        FOREIGN KEY (reinspection_request_id) REFERENCES reinspection_request (id),
    CONSTRAINT fk_reinspection_request_item_checklist_item
        FOREIGN KEY (listing_checklist_item_id) REFERENCES listing_checklist_item (id),
    INDEX idx_reinspection_request_item_request (reinspection_request_id),
    INDEX idx_reinspection_request_item_checklist_item (listing_checklist_item_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS model_checklist_research (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '모델별 체크리스트 리서치 고유 식별자',
    device_model_id BIGINT NOT NULL COMMENT '대상 기기 모델 ID',
    research_version INT NOT NULL COMMENT '리서치 버전',
    status VARCHAR(30) NOT NULL COMMENT '리서치 진행 상태',
    input_snapshot_json LONGTEXT NULL COMMENT '리서치 요청 시점 입력 스냅샷(JSON)',
    result_json LONGTEXT NULL COMMENT 'AI 리서치 결과(JSON)',
    published_template_id BIGINT NULL COMMENT '발행된 체크리스트 템플릿 ID',
    reviewed_by_admin_id BIGINT NULL COMMENT '검수 관리자 ID',
    review_note VARCHAR(500) NULL COMMENT '검수 메모',
    created_at DATETIME(6) NOT NULL COMMENT '생성 시각',
    updated_at DATETIME(6) NOT NULL COMMENT '수정 시각',
    PRIMARY KEY (id),
    CONSTRAINT uk_model_checklist_research_model_version UNIQUE (device_model_id, research_version),
    CONSTRAINT fk_model_checklist_research_template
        FOREIGN KEY (published_template_id) REFERENCES checklist_template (id),
    INDEX idx_model_checklist_research_status_created (status, created_at),
    INDEX idx_model_research_model_updated (device_model_id, updated_at, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS inspection_session (
    session_key CHAR(36) NOT NULL COMMENT 'Windows 자동 검사 세션 고유 키(UUID)',
    pairing_code_hash BINARY(32) NOT NULL COMMENT '페어링 코드 해시값',
    seller_id BIGINT NOT NULL COMMENT '검사 요청 판매자 ID',
    listing_id BIGINT NOT NULL COMMENT '대상 매물 ID',
    status VARCHAR(20) NOT NULL COMMENT '세션 상태',
    agent_token_hash BINARY(32) NULL COMMENT '에이전트 인증 토큰 해시값',
    collector_version VARCHAR(30) NULL COMMENT '수집 프로그램(Windows 앱) 버전',
    expires_at DATETIME(6) NOT NULL COMMENT '세션 만료 시각',
    created_at DATETIME(6) NOT NULL COMMENT '생성 시각',
    paired_at DATETIME(6) NULL COMMENT '에이전트 페어링 완료 시각',
    completed_at DATETIME(6) NULL COMMENT '검사 완료 시각',
    version BIGINT NOT NULL DEFAULT 0 COMMENT '동시성 제어용 낙관적 락 버전',
    PRIMARY KEY (session_key),
    INDEX idx_inspection_session_pairing (pairing_code_hash, status, expires_at),
    INDEX idx_inspection_session_seller_created (seller_id, created_at),
    INDEX idx_inspection_session_expires (expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='Windows 자동 검사 일회용 연결 세션';

CREATE TABLE IF NOT EXISTS inspection_session_test_result (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '검사 결과 고유 식별자',
    session_key CHAR(36) NOT NULL COMMENT '소속 검사 세션 키',
    listing_checklist_item_id BIGINT NULL COMMENT '대상 체크리스트 항목 ID',
    client_result_id BINARY(16) NOT NULL COMMENT '클라이언트가 부여한 결과 고유 ID(중복 제출 방지)',
    test_type VARCHAR(30) NOT NULL COMMENT '검사 항목 유형',
    measurement_status VARCHAR(30) NOT NULL COMMENT '측정 상태',
    user_result VARCHAR(30) NULL COMMENT '사용자 판정 결과',
    measured_values JSON NULL COMMENT '측정값(JSON)',
    attempt_no INT UNSIGNED NOT NULL COMMENT '시도 회차',
    raw_data_saved BIT(1) NOT NULL DEFAULT b'0' COMMENT '원본 데이터 저장 여부',
    tested_at DATETIME(6) NOT NULL COMMENT '검사 수행 시각',
    created_at DATETIME(6) NOT NULL COMMENT '결과 저장 시각',
    error_code VARCHAR(100) NULL COMMENT '오류 코드',
    PRIMARY KEY (id),
    CONSTRAINT uk_inspection_test_result_client UNIQUE (session_key, client_result_id),
    CONSTRAINT uk_inspection_test_result_attempt UNIQUE (session_key, test_type, attempt_no),
    CONSTRAINT fk_inspection_test_result_session
        FOREIGN KEY (session_key) REFERENCES inspection_session (session_key),
    CONSTRAINT fk_inspection_test_result_checklist_item
        FOREIGN KEY (listing_checklist_item_id) REFERENCES listing_checklist_item (id),
    INDEX idx_inspection_test_result_session_created (session_key, created_at, id),
    INDEX idx_inspection_test_result_checklist_item (listing_checklist_item_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='Windows 선택검사 결과 이력';


-- =========================================================================
-- SECTION 5. 채팅 / 통화 / RTC
-- =========================================================================

CREATE TABLE IF NOT EXISTS chat_room (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '고유 식별자',
    listing_id BIGINT NOT NULL COMMENT '연결된 매물 ID',
    transaction_id BIGINT NULL COMMENT '연결된 결제 ID (payment.payment_id 참조)',
    buyer_id BIGINT NOT NULL COMMENT '구매자 회원 ID',
    seller_id BIGINT NOT NULL COMMENT '판매자 회원 ID',
    status VARCHAR(20) NOT NULL COMMENT '채팅방 상태',
    last_message_id BIGINT NULL COMMENT '마지막 메시지 ID (chat_message와 순환 참조라 FK 제약 없이 값만 저장)',
    last_message_seq BIGINT NOT NULL COMMENT '마지막 메시지 시퀀스',
    last_message_at DATETIME(6) NULL COMMENT '마지막 메시지 시각',
    created_at DATETIME(6) NOT NULL COMMENT '생성 시각',
    updated_at DATETIME(6) NOT NULL COMMENT '수정 시각',
    closed_at DATETIME(6) NULL COMMENT '종료 시각',
    PRIMARY KEY (id),
    CONSTRAINT uk_chat_room_listing_users UNIQUE (listing_id, buyer_id, seller_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS chat_room_participant (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '고유 식별자',
    chat_room_id BIGINT NOT NULL COMMENT '소속 채팅방 ID',
    user_id BIGINT NOT NULL COMMENT '참여 회원 ID',
    participant_role VARCHAR(20) NOT NULL COMMENT '참여 역할',
    last_read_seq BIGINT NOT NULL COMMENT '마지막으로 읽은 시퀀스',
    joined_at DATETIME(6) NOT NULL COMMENT '참여 시각',
    left_at DATETIME(6) NULL COMMENT '퇴장 시각',
    created_at DATETIME(6) NOT NULL COMMENT '생성 시각',
    updated_at DATETIME(6) NOT NULL COMMENT '수정 시각',
    PRIMARY KEY (id),
    CONSTRAINT uk_chat_room_participant_user UNIQUE (chat_room_id, user_id),
    CONSTRAINT uk_chat_room_participant_role UNIQUE (chat_room_id, participant_role),
    CONSTRAINT fk_chat_room_participant_room FOREIGN KEY (chat_room_id) REFERENCES chat_room (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS chat_message (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '고유 식별자',
    chat_room_id BIGINT NOT NULL COMMENT '소속 채팅방 ID',
    room_sequence BIGINT NOT NULL COMMENT '방 내 시퀀스',
    sender_id BIGINT NOT NULL COMMENT '발신 회원 ID',
    client_message_id BINARY(16) NOT NULL COMMENT '클라이언트 멱등키',
    message_type VARCHAR(20) NOT NULL COMMENT '메시지 유형',
    content LONGTEXT NULL COMMENT '텍스트 내용',
    status VARCHAR(20) NOT NULL COMMENT '메시지 상태',
    sent_at DATETIME(6) NOT NULL COMMENT '전송 시각',
    deleted_at DATETIME(6) NULL COMMENT '삭제 시각',
    PRIMARY KEY (id),
    CONSTRAINT uk_chat_message_room_sequence UNIQUE (chat_room_id, room_sequence),
    CONSTRAINT uk_chat_message_client UNIQUE (chat_room_id, client_message_id),
    CONSTRAINT fk_chat_message_room FOREIGN KEY (chat_room_id) REFERENCES chat_room (id),
    INDEX ix_chat_message_room_sent (chat_room_id, sent_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS chat_media (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '고유 식별자',
    media_key BINARY(16) NOT NULL COMMENT '외부 노출용 키',
    chat_room_id BIGINT NOT NULL COMMENT '소속 채팅방 ID',
    uploader_id BIGINT NOT NULL COMMENT '업로드 회원 ID',
    media_type VARCHAR(20) NOT NULL COMMENT '미디어 유형',
    upload_status VARCHAR(20) NOT NULL COMMENT '업로드 상태',
    bucket_name VARCHAR(255) NOT NULL COMMENT '버킷명',
    object_key VARCHAR(1024) NOT NULL COMMENT '오브젝트 키',
    original_filename VARCHAR(255) NOT NULL COMMENT '원본 파일명',
    mime_type VARCHAR(100) NOT NULL COMMENT 'MIME 타입',
    file_size_bytes BIGINT NOT NULL COMMENT '파일 크기(byte)',
    width_px INT NULL COMMENT '가로 픽셀',
    height_px INT NULL COMMENT '세로 픽셀',
    duration_ms BIGINT NULL COMMENT '재생 시간(ms)',
    upload_expires_at DATETIME(6) NOT NULL COMMENT '업로드 URL 만료 시각',
    verified_at DATETIME(6) NULL COMMENT '검증 완료 시각',
    PRIMARY KEY (id),
    CONSTRAINT uk_chat_media_key UNIQUE (media_key),
    CONSTRAINT uk_chat_media_object UNIQUE (bucket_name, object_key(255)),
    CONSTRAINT fk_chat_media_room FOREIGN KEY (chat_room_id) REFERENCES chat_room (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS chat_message_media (
    chat_message_id BIGINT NOT NULL COMMENT '대상 메시지 ID',
    chat_media_id BIGINT NOT NULL COMMENT '대상 미디어 ID',
    display_order INT NOT NULL COMMENT '노출 순서',
    created_at DATETIME(6) NOT NULL COMMENT '생성 시각',
    PRIMARY KEY (chat_message_id, chat_media_id),
    CONSTRAINT fk_chat_message_media_message FOREIGN KEY (chat_message_id) REFERENCES chat_message (id),
    CONSTRAINT fk_chat_message_media_media FOREIGN KEY (chat_media_id) REFERENCES chat_media (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS chat_outbox_event (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '고유 식별자',
    event_id BINARY(16) NOT NULL COMMENT '이벤트 고유 키',
    aggregate_type VARCHAR(100) NOT NULL COMMENT '애그리거트 유형',
    aggregate_id BIGINT NOT NULL COMMENT '애그리거트 ID (다형 참조, FK 없음)',
    event_type VARCHAR(100) NOT NULL COMMENT '이벤트 유형',
    payload LONGTEXT NOT NULL COMMENT '이벤트 페이로드',
    status VARCHAR(20) NOT NULL COMMENT '발행 상태',
    retry_count INT NOT NULL COMMENT '재시도 횟수',
    next_retry_at DATETIME(6) NULL COMMENT '다음 재시도 시각',
    published_at DATETIME(6) NULL COMMENT '발행 완료 시각',
    created_at DATETIME(6) NOT NULL COMMENT '생성 시각',
    PRIMARY KEY (id),
    CONSTRAINT uk_chat_outbox_event_id UNIQUE (event_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS call_appointment (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '고유 식별자',
    appointment_key BINARY(16) NOT NULL COMMENT '외부 노출용 키',
    chat_room_id BIGINT NOT NULL COMMENT '소속 채팅방 ID',
    proposer_id BIGINT NOT NULL COMMENT '제안 회원 ID',
    respondent_id BIGINT NOT NULL COMMENT '응답 회원 ID',
    status VARCHAR(20) NOT NULL COMMENT '약속 상태',
    scheduled_at DATETIME(6) NOT NULL COMMENT '예정 시각',
    memo VARCHAR(500) NULL COMMENT '메모',
    cancel_reason VARCHAR(500) NULL COMMENT '취소 사유',
    version BIGINT NOT NULL COMMENT '낙관적 락',
    responded_at DATETIME(6) NULL COMMENT '응답 시각',
    canceled_at DATETIME(6) NULL COMMENT '취소 시각',
    completed_at DATETIME(6) NULL COMMENT '완료 시각',
    PRIMARY KEY (id),
    CONSTRAINT uk_call_appointment_key UNIQUE (appointment_key),
    CONSTRAINT fk_call_appointment_room FOREIGN KEY (chat_room_id) REFERENCES chat_room (id),
    INDEX ix_call_appointment_room_schedule (chat_room_id, scheduled_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS call_appointment_message (
    call_appointment_id BIGINT NOT NULL COMMENT '대상 약속 ID',
    chat_message_id BIGINT NOT NULL COMMENT '대상 메시지 ID',
    appointment_event VARCHAR(20) NOT NULL COMMENT '약속 이벤트 유형',
    created_at DATETIME(6) NOT NULL COMMENT '생성 시각',
    PRIMARY KEY (call_appointment_id, chat_message_id),
    CONSTRAINT fk_call_appointment_message_appointment FOREIGN KEY (call_appointment_id) REFERENCES call_appointment (id),
    CONSTRAINT fk_call_appointment_message_message FOREIGN KEY (chat_message_id) REFERENCES chat_message (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS rtc_session (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '고유 식별자',
    session_key BINARY(16) NOT NULL COMMENT '외부 노출용 키',
    call_appointment_id BIGINT NULL COMMENT '연결된 약속 ID',
    chat_room_id BIGINT NOT NULL COMMENT '소속 채팅방 ID',
    listing_id BIGINT NOT NULL COMMENT '대상 매물 ID',
    transaction_id BIGINT NULL COMMENT '연결된 결제 ID (payment.payment_id 참조)',
    seller_id BIGINT NOT NULL COMMENT '판매자 회원 ID(송출자)',
    buyer_id BIGINT NOT NULL COMMENT '구매자 회원 ID(수신자)',
    media_direction VARCHAR(30) NOT NULL COMMENT '미디어 송출 방향(판매자 카메라 전용)',
    status VARCHAR(20) NOT NULL COMMENT '세션 상태',
    connection_type VARCHAR(20) NULL COMMENT '연결 경로',
    end_reason VARCHAR(30) NULL COMMENT '종료 사유',
    verification_memo VARCHAR(1000) NULL COMMENT '검수 확인 메모',
    expires_at DATETIME(6) NOT NULL COMMENT '만료 시각',
    connected_at DATETIME(6) NULL COMMENT '연결 성립 시각',
    inspection_submitted_at DATETIME(6) NULL COMMENT '화상 통화 중 검수 자료 제출 완료 시각',
    ended_at DATETIME(6) NULL COMMENT '종료 시각',
    PRIMARY KEY (id),
    CONSTRAINT uk_rtc_session_key UNIQUE (session_key),
    CONSTRAINT uk_rtc_session_appointment UNIQUE (call_appointment_id),
    CONSTRAINT fk_rtc_session_appointment FOREIGN KEY (call_appointment_id) REFERENCES call_appointment (id),
    CONSTRAINT fk_rtc_session_room FOREIGN KEY (chat_room_id) REFERENCES chat_room (id),
    INDEX ix_rtc_session_participants_status (seller_id, buyer_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS rtc_session_summary (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '고유 식별자',
    rtc_session_id BIGINT NOT NULL COMMENT '대상 세션 ID',
    connection_setup_ms BIGINT NULL COMMENT '연결 성립까지 걸린 시간(ms)',
    used_turn BIT(1) NOT NULL COMMENT 'TURN 경유 여부',
    avg_rtt_ms DECIMAL(12,3) NULL COMMENT '평균 RTT(ms)',
    max_rtt_ms DECIMAL(12,3) NULL COMMENT '최대 RTT(ms)',
    avg_packet_loss_rate DECIMAL(8,5) NULL COMMENT '평균 패킷 손실률',
    avg_jitter_ms DECIMAL(12,3) NULL COMMENT '평균 지터(ms)',
    avg_inbound_bitrate_kbps DECIMAL(14,3) NULL COMMENT '평균 수신 비트레이트(kbps)',
    avg_fps DECIMAL(8,3) NULL COMMENT '평균 FPS',
    ice_restart_count INT NOT NULL COMMENT 'ICE 재시작 횟수',
    reconnect_count INT NOT NULL COMMENT '재연결 횟수',
    recovery_succeeded BIT(1) NOT NULL COMMENT '복구 성공 여부',
    calculated_at DATETIME(6) NOT NULL COMMENT '집계 시각',
    PRIMARY KEY (id),
    CONSTRAINT uk_rtc_session_summary_session UNIQUE (rtc_session_id),
    CONSTRAINT fk_rtc_session_summary_session FOREIGN KEY (rtc_session_id) REFERENCES rtc_session (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS rtc_session_checklist_result (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '고유 식별자',
    rtc_session_id BIGINT NOT NULL COMMENT '대상 RTC 세션 ID',
    listing_checklist_item_id BIGINT NOT NULL COMMENT '확인 대상 체크리스트 항목 ID',
    checked_by BIGINT NOT NULL COMMENT '확인을 수행한 회원 ID',
    is_confirmed BIT(1) NOT NULL COMMENT '확인 완료 여부',
    note VARCHAR(500) NULL COMMENT '메모',
    checked_at DATETIME(6) NOT NULL COMMENT '확인 시각',
    PRIMARY KEY (id),
    CONSTRAINT uk_rtc_checklist_session_item UNIQUE (rtc_session_id, listing_checklist_item_id),
    CONSTRAINT fk_rtc_checklist_session FOREIGN KEY (rtc_session_id) REFERENCES rtc_session (id),
    INDEX ix_rtc_checklist_session (rtc_session_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS reinspection_request_message (
    reinspection_request_id BIGINT NOT NULL COMMENT '재검수 요청 ID',
    event_type VARCHAR(30) NOT NULL COMMENT '채팅 알림 종류',
    chat_message_id BIGINT NOT NULL COMMENT '자동 생성된 SYSTEM 채팅 메시지 ID',
    created_at DATETIME(6) NOT NULL COMMENT '생성 시각',
    PRIMARY KEY (reinspection_request_id, event_type),
    CONSTRAINT uk_reinspection_request_message_chat_message UNIQUE (chat_message_id),
    CONSTRAINT fk_reinspection_request_message_chat_message FOREIGN KEY (chat_message_id) REFERENCES chat_message (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


-- =========================================================================
-- SECTION 6. 교차 도메인 FK (위 5개 섹션의 테이블이 서로 다른 섹션을 참조하는 경우)
-- 전부 여기서 ALTER TABLE로 추가하므로, 섹션 1~5는 순서와 무관하게 실행 가능하다.
-- =========================================================================

-- SECTION 2 -> SECTION 1 / SECTION 4
ALTER TABLE listing
    ADD CONSTRAINT fk_listing_seller FOREIGN KEY (seller_id) REFERENCES user_account (user_id);
ALTER TABLE listing
    ADD CONSTRAINT fk_listing_buyer FOREIGN KEY (buyer_id) REFERENCES user_account (user_id);
ALTER TABLE listing
    ADD CONSTRAINT fk_listing_checklist_template FOREIGN KEY (checklist_template_id) REFERENCES checklist_template (id);
ALTER TABLE media_upload_session
    ADD CONSTRAINT fk_media_upload_session_checklist_item FOREIGN KEY (listing_checklist_item_id) REFERENCES listing_checklist_item (id);
ALTER TABLE media_upload_session
    ADD CONSTRAINT fk_media_upload_session_uploader FOREIGN KEY (uploader_id) REFERENCES user_account (user_id);
ALTER TABLE wishlist
    ADD CONSTRAINT fk_wishlist_user FOREIGN KEY (user_id) REFERENCES user_account (user_id);
ALTER TABLE device_model
    ADD CONSTRAINT fk_device_model_reporter FOREIGN KEY (reported_by_member_id) REFERENCES user_account (user_id);
ALTER TABLE device_model
    ADD CONSTRAINT fk_device_model_reviewer FOREIGN KEY (reviewed_by_admin_id) REFERENCES admin_account (admin_id);
ALTER TABLE device_model
    ADD CONSTRAINT fk_device_model_disabler FOREIGN KEY (disabled_by_admin_id) REFERENCES admin_account (admin_id);
ALTER TABLE device_model_request
    ADD CONSTRAINT fk_device_model_request_requester FOREIGN KEY (requested_by_member_id) REFERENCES user_account (user_id);
ALTER TABLE device_model_request
    ADD CONSTRAINT fk_device_model_request_reviewer FOREIGN KEY (reviewed_by_admin_id) REFERENCES admin_account (admin_id);
ALTER TABLE listing_report
    ADD CONSTRAINT fk_listing_report_reporter FOREIGN KEY (reporter_id) REFERENCES user_account (user_id);
ALTER TABLE listing_restoration_request
    ADD CONSTRAINT fk_listing_restoration_seller FOREIGN KEY (seller_id) REFERENCES user_account (user_id);
ALTER TABLE moderation_risk_signal
    ADD CONSTRAINT fk_moderation_risk_seller FOREIGN KEY (seller_id) REFERENCES user_account (user_id);

-- SECTION 3 -> SECTION 1 / SECTION 2
ALTER TABLE seller
    ADD CONSTRAINT fk_seller_user FOREIGN KEY (user_id) REFERENCES user_account (user_id);
ALTER TABLE payment
    ADD CONSTRAINT fk_payment_listing FOREIGN KEY (listing_id) REFERENCES listing (id);
ALTER TABLE payment
    ADD CONSTRAINT fk_payment_buyer FOREIGN KEY (buyer_id) REFERENCES user_account (user_id);
ALTER TABLE refund_request
    ADD CONSTRAINT fk_refund_request_processed_admin FOREIGN KEY (processed_admin_id) REFERENCES admin_account (admin_id);
ALTER TABLE settlement
    ADD CONSTRAINT fk_settlement_listing FOREIGN KEY (listing_id) REFERENCES listing (id);
ALTER TABLE settlement
    ADD CONSTRAINT fk_settlement_seller FOREIGN KEY (seller_id) REFERENCES user_account (user_id);

-- SECTION 4 -> SECTION 1 / SECTION 2 / SECTION 5
ALTER TABLE checklist_template
    ADD CONSTRAINT fk_checklist_template_category FOREIGN KEY (category_id) REFERENCES category (id);
ALTER TABLE listing_checklist_item
    ADD CONSTRAINT fk_listing_checklist_item_listing FOREIGN KEY (listing_id) REFERENCES listing (id);
ALTER TABLE evidence
    ADD CONSTRAINT fk_evidence_listing FOREIGN KEY (listing_id) REFERENCES listing (id);
ALTER TABLE listing_account_removal_check
    ADD CONSTRAINT fk_listing_account_removal_check_listing FOREIGN KEY (listing_id) REFERENCES listing (id);
ALTER TABLE reinspection_request
    ADD CONSTRAINT fk_reinspection_request_listing FOREIGN KEY (listing_id) REFERENCES listing (id),
    ADD CONSTRAINT fk_reinspection_request_chat_room FOREIGN KEY (chat_room_id) REFERENCES chat_room (id),
    ADD CONSTRAINT fk_reinspection_request_buyer FOREIGN KEY (buyer_id) REFERENCES user_account (user_id),
    ADD CONSTRAINT fk_reinspection_request_seller FOREIGN KEY (seller_id) REFERENCES user_account (user_id);
ALTER TABLE model_checklist_research
    ADD CONSTRAINT fk_model_checklist_research_model FOREIGN KEY (device_model_id) REFERENCES device_model (model_id);
ALTER TABLE model_checklist_research
    ADD CONSTRAINT fk_model_checklist_research_reviewer FOREIGN KEY (reviewed_by_admin_id) REFERENCES admin_account (admin_id);
ALTER TABLE inspection_session
    ADD CONSTRAINT fk_inspection_session_listing FOREIGN KEY (listing_id) REFERENCES listing (id),
    ADD CONSTRAINT fk_inspection_session_seller FOREIGN KEY (seller_id) REFERENCES user_account (user_id);

-- SECTION 5 -> SECTION 1 / SECTION 2 / SECTION 3 / SECTION 4
ALTER TABLE chat_room
    ADD CONSTRAINT fk_chat_room_listing FOREIGN KEY (listing_id) REFERENCES listing (id),
    ADD CONSTRAINT fk_chat_room_buyer FOREIGN KEY (buyer_id) REFERENCES user_account (user_id),
    ADD CONSTRAINT fk_chat_room_seller FOREIGN KEY (seller_id) REFERENCES user_account (user_id),
    ADD CONSTRAINT fk_chat_room_transaction FOREIGN KEY (transaction_id) REFERENCES payment (payment_id);
ALTER TABLE chat_room_participant
    ADD CONSTRAINT fk_chat_room_participant_user FOREIGN KEY (user_id) REFERENCES user_account (user_id);
ALTER TABLE chat_message
    ADD CONSTRAINT fk_chat_message_sender FOREIGN KEY (sender_id) REFERENCES user_account (user_id);
ALTER TABLE chat_media
    ADD CONSTRAINT fk_chat_media_uploader FOREIGN KEY (uploader_id) REFERENCES user_account (user_id);
ALTER TABLE call_appointment
    ADD CONSTRAINT fk_call_appointment_proposer FOREIGN KEY (proposer_id) REFERENCES user_account (user_id),
    ADD CONSTRAINT fk_call_appointment_respondent FOREIGN KEY (respondent_id) REFERENCES user_account (user_id);
ALTER TABLE rtc_session
    ADD CONSTRAINT fk_rtc_session_listing FOREIGN KEY (listing_id) REFERENCES listing (id),
    ADD CONSTRAINT fk_rtc_session_seller FOREIGN KEY (seller_id) REFERENCES user_account (user_id),
    ADD CONSTRAINT fk_rtc_session_buyer FOREIGN KEY (buyer_id) REFERENCES user_account (user_id),
    ADD CONSTRAINT fk_rtc_session_transaction FOREIGN KEY (transaction_id) REFERENCES payment (payment_id);
ALTER TABLE rtc_session_checklist_result
    ADD CONSTRAINT fk_rtc_checklist_item FOREIGN KEY (listing_checklist_item_id) REFERENCES listing_checklist_item (id),
    ADD CONSTRAINT fk_rtc_checklist_member FOREIGN KEY (checked_by) REFERENCES user_account (user_id);
ALTER TABLE reinspection_request_message
    ADD CONSTRAINT fk_reinspection_request_message_request FOREIGN KEY (reinspection_request_id) REFERENCES reinspection_request (id);
