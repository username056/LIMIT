
-- category — 카테고리/기기유형 (자기참조 트리, 리프 = 모델)
CREATE TABLE category (
    id            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '카테고리 고유 식별자',
    parent_id     BIGINT       NULL COMMENT '상위 카테고리 ID(자기참조 FK). 대분류/소분류 계층, 최상위는 NULL',
    name          VARCHAR(100) NOT NULL COMMENT '화면 노출명 (예: "스마트폰" → "삼성 폴더블" → 리프 "Galaxy S24")',
    device_type   ENUM('SMARTPHONE','FOLDABLE','TABLET','LAPTOP') NOT NULL COMMENT '기기 대분류. 폴더블은 힌지 항목 때문에 분리',
    manufacturer  VARCHAR(50)  NULL COMMENT '제조사명(예: Samsung, Apple). 대분류는 NULL, 세부 카테고리에만 채움',
    os_family     ENUM('IOS','ANDROID','WINDOWS','MACOS') NULL COMMENT '운영체제 계열. 템플릿 OS별 매칭 보조 키',
    model_code    VARCHAR(50)  NULL COMMENT '모델코드(예: SM-S921N). 리프(모델)에만 채움, 리프 판별 겸용',
    display_order INT          NOT NULL DEFAULT 0 COMMENT '카테고리 선택 화면 정렬 순서(낮을수록 먼저)',
    is_active     BOOLEAN      NOT NULL DEFAULT TRUE COMMENT '등록 폼 노출 여부. 2차 범위(Mac 등)는 만들어두고 꺼둠',
    PRIMARY KEY (id),
    UNIQUE KEY uk_category_model_code (model_code),
    CONSTRAINT fk_category_parent FOREIGN KEY (parent_id) REFERENCES category (id)
) COMMENT = '카테고리/기기유형. 리프 카테고리가 곧 기기 모델';

-- checklist_template — 모델별 체크리스트 템플릿 (버전 관리, PUBLISHED 후 불변)
CREATE TABLE checklist_template (
    id           BIGINT   NOT NULL AUTO_INCREMENT COMMENT '템플릿 고유 식별자',
    category_id  BIGINT   NOT NULL COMMENT '리프(모델) 카테고리 ID (FK -> category.id). 템플릿 매칭 키',
    version      INT      NOT NULL COMMENT '템플릿 버전. 수정 = 새 버전 발행',
    status       ENUM('DRAFT','PUBLISHED','DEPRECATED') NOT NULL DEFAULT 'DRAFT' COMMENT 'PUBLISHED 후 항목 수정 금지 — listing 스냅샷 유효성의 전제',
    published_at DATETIME NULL COMMENT '발행 시각',
    created_at   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성일시',
    PRIMARY KEY (id),
    UNIQUE KEY uk_template_category_version (category_id, version),
    CONSTRAINT fk_template_category FOREIGN KEY (category_id) REFERENCES category (id)
) COMMENT = '모델별 체크리스트 템플릿(버전 관리)';

-- checklist_template_item — 템플릿 항목 정의
CREATE TABLE checklist_template_item (
    id                       BIGINT       NOT NULL AUTO_INCREMENT COMMENT '템플릿 항목 고유 식별자',
    checklist_template_id    BIGINT       NOT NULL COMMENT '소속 템플릿 ID (FK)',
    item_code                VARCHAR(30)  NOT NULL COMMENT '항목 코드(예: SP-EXT-001)',
    name                     VARCHAR(100) NOT NULL COMMENT '항목명',
    purpose                  VARCHAR(200) NOT NULL COMMENT '확인 목적',
    capture_guide            TEXT         NOT NULL COMMENT '판매자 촬영 안내',
    evidence_type            ENUM('PHOTO','VIDEO','DIAGNOSTIC_FILE','SELLER_CONFIRMATION') NOT NULL COMMENT '증거 유형',
    is_required              BOOLEAN      NOT NULL DEFAULT TRUE COMMENT '필수 여부. 필수 미완료 시 판매 게시 불가',
    allowed_formats          VARCHAR(100) NULL COMMENT '허용 파일 형식(콤마 구분: jpg,png,heic)',
    min_count                INT          NULL COMMENT '사진 최소 개수',
    max_count                INT          NULL COMMENT '사진 최대 개수',
    min_duration_sec         INT          NULL COMMENT '영상 최소 길이(초)',
    max_duration_sec         INT          NULL COMMENT '영상 최대 길이(초)',
    max_file_size_mb         INT          NULL COMMENT '파일 최대 용량(MB)',
    recapture_allowed        BOOLEAN      NOT NULL DEFAULT TRUE COMMENT '재촬영 가능 여부',
    visible_to_buyer         BOOLEAN      NOT NULL DEFAULT TRUE COMMENT '구매자 공개 여부(false면 완료 상태만 공개)',
    privacy_masking_required BOOLEAN      NOT NULL DEFAULT FALSE COMMENT '개인정보 마스킹 필요 여부',
    display_order            INT          NOT NULL DEFAULT 0 COMMENT '정렬 순서',
    PRIMARY KEY (id),
    UNIQUE KEY uk_item_template_code (checklist_template_id, item_code),
    CONSTRAINT fk_item_template FOREIGN KEY (checklist_template_id) REFERENCES checklist_template (id)
) COMMENT = '체크리스트 템플릿 항목 정의';

-- listing — 매물 (상품 정보 + 거래 상태)
CREATE TABLE listing (
    id                    BIGINT       NOT NULL AUTO_INCREMENT COMMENT '매물 고유 식별자',
    seller_id             BIGINT       NOT NULL COMMENT '판매자 회원 ID (FK -> user.id)',
    category_id           BIGINT       NOT NULL COMMENT '매물이 속한 리프(모델) 카테고리 (FK -> category.id)',
    title                 VARCHAR(200) NOT NULL COMMENT '매물 제목',
    description           TEXT         NULL COMMENT '매물 상세 설명',
    price                 INT          NOT NULL COMMENT '판매 가격',
    checklist_template_id BIGINT       NOT NULL COMMENT '등록 시점 적용 템플릿. 버전업돼도 등록 당시 버전으로 고정(스냅샷)',
    precheck_completed    BOOLEAN      NOT NULL DEFAULT FALSE COMMENT '판매 전 계정 제거 안내 자가 체크(경고성). ※계정 제거를 필수 증거 항목으로 할지 팀 결정 대기',
    status                ENUM('DRAFT','ON_SALE','RESERVED','PAID','INSPECTING','CONFIRMED','SETTLED','CANCELLED','HIDDEN','SUSPENDED') NOT NULL DEFAULT 'DRAFT' COMMENT '매물 라이프사이클 상태(하단 주석 참고)',
    suspended_reason      VARCHAR(200) NULL COMMENT '관리자 제재 사유(SUSPENDED일 때)',
    buyer_id              BIGINT       NULL COMMENT '거래 중 구매자 (FK -> user.id). 예약 시 채움, 타임아웃 시 NULL 초기화',
    reserved_at           DATETIME     NULL COMMENT '예약 시각. buyer_id와 함께 타임아웃 시 초기화',
    paid_at               DATETIME     NULL COMMENT '결제 승인 시각(에스크로)',
    confirmed_at          DATETIME     NULL COMMENT '구매확정 시각',
    settled_at            DATETIME     NULL COMMENT '정산 완료 시각(실제 송금 아님, 상태 전이 시연용)',
    version               BIGINT       NOT NULL DEFAULT 0 COMMENT '낙관적 잠금. 동시 예약 1건만 성공 보장',
    deleted_at            DATETIME     NULL COMMENT '논리 삭제 시각. SETTLED(거래 완료) 매물은 삭제 금지 — 기록 보존',
    created_at            DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '매물 등록 시각',
    updated_at            DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '마지막 수정 시각(자동 갱신)',
    PRIMARY KEY (id),
    KEY idx_listing_status (status),
    KEY idx_listing_seller (seller_id),
    KEY idx_listing_category (category_id),
    CONSTRAINT fk_listing_category FOREIGN KEY (category_id) REFERENCES category (id),
    CONSTRAINT fk_listing_template FOREIGN KEY (checklist_template_id) REFERENCES checklist_template (id)
) COMMENT = '매물. 기기 1대 = 매물 1건, 거래 상태 직접 소유';

-- listing_image — 매물 소개 이미지 (증거 사진과 별개)
CREATE TABLE listing_image (
    id         BIGINT       NOT NULL AUTO_INCREMENT COMMENT '이미지 고유 식별자',
    listing_id BIGINT       NOT NULL COMMENT '소속 매물 ID (FK)',
    image_type ENUM('THUMBNAIL','DETAIL') NOT NULL COMMENT '이미지 유형(대표/상세)',
    s3_key     VARCHAR(500) NOT NULL COMMENT 'S3 원본 경로(내부용, API 응답 비노출)',
    cdn_url    VARCHAR(500) NOT NULL COMMENT 'CloudFront 접근 URL(클라이언트 조회용)',
    mime_type  VARCHAR(50)  NOT NULL COMMENT '파일 MIME 타입(악성 파일 검증 기록)',
    created_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '등록일시',
    PRIMARY KEY (id),
    CONSTRAINT fk_image_listing FOREIGN KEY (listing_id) REFERENCES listing (id)
) COMMENT = '매물 소개 이미지';

-- listing_checklist_item — 매물별 체크리스트 스냅샷 항목
CREATE TABLE listing_checklist_item (
    id                       BIGINT       NOT NULL AUTO_INCREMENT COMMENT '스냅샷 항목 고유 식별자',
    listing_id               BIGINT       NOT NULL COMMENT '소속 매물 ID (FK)',
    template_item_id         BIGINT       NOT NULL COMMENT '원본 템플릿 항목 ID (FK)',
    item_code                VARCHAR(30)  NOT NULL COMMENT '항목 코드(스냅샷 복사)',
    name                     VARCHAR(100) NOT NULL COMMENT '항목명(스냅샷 복사)',
    capture_guide            TEXT         NOT NULL COMMENT '촬영 안내(스냅샷 복사)',
    evidence_type            ENUM('PHOTO','VIDEO','DIAGNOSTIC_FILE','SELLER_CONFIRMATION') NOT NULL COMMENT '증거 유형',
    is_required              BOOLEAN      NOT NULL COMMENT '필수 여부',
    allowed_formats          VARCHAR(100) NULL COMMENT '허용 파일 형식',
    min_count                INT          NULL COMMENT '사진 최소 개수',
    max_count                INT          NULL COMMENT '사진 최대 개수',
    min_duration_sec         INT          NULL COMMENT '영상 최소 길이(초)',
    max_duration_sec         INT          NULL COMMENT '영상 최대 길이(초)',
    max_file_size_mb         INT          NULL COMMENT '파일 최대 용량(MB)',
    recapture_allowed        BOOLEAN      NOT NULL DEFAULT TRUE COMMENT '재촬영 가능 여부',
    visible_to_buyer         BOOLEAN      NOT NULL DEFAULT TRUE COMMENT '구매자 공개 여부',
    privacy_masking_required BOOLEAN      NOT NULL DEFAULT FALSE COMMENT '개인정보 마스킹 필요 여부',
    completion_status        ENUM('PENDING','SUBMITTED','COMPLETED') NOT NULL DEFAULT 'PENDING' COMMENT '항목 완료 상태',
    display_order            INT          NOT NULL DEFAULT 0 COMMENT '정렬 순서',
    PRIMARY KEY (id),
    UNIQUE KEY uk_lci_listing_code (listing_id, item_code),
    CONSTRAINT fk_lci_listing FOREIGN KEY (listing_id) REFERENCES listing (id),
    CONSTRAINT fk_lci_template_item FOREIGN KEY (template_item_id) REFERENCES checklist_template_item (id)
) COMMENT = '매물별 체크리스트 스냅샷(등록 시점 템플릿 복사, 이후 템플릿 변경 무영향)';

-- evidence — 항목별 증거 (재촬영 이력 보존)
CREATE TABLE evidence (
    id                        BIGINT       NOT NULL AUTO_INCREMENT COMMENT '증거 고유 식별자',
    listing_id                BIGINT       NOT NULL COMMENT '소속 매물 ID (FK)',
    listing_checklist_item_id BIGINT       NOT NULL COMMENT '대상 스냅샷 항목 ID (FK)',
    evidence_type             ENUM('PHOTO','VIDEO','DIAGNOSTIC_FILE','SELLER_CONFIRMATION') NOT NULL COMMENT '증거 유형(항목 유형과 일치 검증)',
    s3_key                    VARCHAR(500) NOT NULL COMMENT 'S3 원본 경로(내부용)',
    cdn_url                   VARCHAR(500) NULL COMMENT 'CDN 접근 URL(처리 완료 후 채움)',
    mime_type                 VARCHAR(50)  NOT NULL COMMENT '파일 MIME 타입',
    attempt_no                INT          NOT NULL DEFAULT 1 COMMENT '시도 번호. 재촬영 시 +1, 이전 행 보존',
    is_latest                 BOOLEAN      NOT NULL DEFAULT TRUE COMMENT '최신 증거 여부. 항목당 true 1건',
    captured_at               DATETIME     NULL COMMENT '촬영 시각(메타데이터)',
    uploaded_at               DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '업로드 완료 시각',
    processing_status         ENUM('PENDING','PROCESSING','READY','FAILED','INFECTED') NOT NULL DEFAULT 'PENDING' COMMENT '미디어 처리 상태',
    buyer_confirmation_status ENUM('NONE','CONFIRMED','RECAPTURE_REQUESTED') NOT NULL DEFAULT 'NONE' COMMENT '구매자 확인 상태',
    PRIMARY KEY (id),
    UNIQUE KEY uk_evidence_item_attempt (listing_checklist_item_id, attempt_no),
    KEY idx_evidence_item_latest (listing_checklist_item_id, is_latest),
    CONSTRAINT fk_evidence_listing FOREIGN KEY (listing_id) REFERENCES listing (id),
    CONSTRAINT fk_evidence_lci FOREIGN KEY (listing_checklist_item_id) REFERENCES listing_checklist_item (id)
) COMMENT = '항목별 증거. 재촬영해도 이전 증거 삭제하지 않고 이력 보존';

-- recapture_request — 구매자 재촬영 요청
CREATE TABLE recapture_request (
    id                        BIGINT       NOT NULL AUTO_INCREMENT COMMENT '재촬영 요청 고유 식별자',
    listing_id                BIGINT       NOT NULL COMMENT '대상 매물 ID (FK)',
    listing_checklist_item_id BIGINT       NOT NULL COMMENT '대상 스냅샷 항목 ID (FK)',
    target_evidence_id        BIGINT       NOT NULL COMMENT '재촬영 요청 대상 증거 ID (FK)',
    requester_id              BIGINT       NOT NULL COMMENT '요청자 회원 ID(구매자/예약자)',
    reason                    VARCHAR(500) NOT NULL COMMENT '요청 사유',
    status                    ENUM('REQUESTED','FULFILLED','REJECTED','CANCELLED') NOT NULL DEFAULT 'REQUESTED' COMMENT '요청 처리 상태',
    created_at                DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '요청 시각',
    resolved_at               DATETIME     NULL COMMENT '처리 완료 시각',
    PRIMARY KEY (id),
    CONSTRAINT fk_recapture_listing FOREIGN KEY (listing_id) REFERENCES listing (id),
    CONSTRAINT fk_recapture_lci FOREIGN KEY (listing_checklist_item_id) REFERENCES listing_checklist_item (id),
    CONSTRAINT fk_recapture_evidence FOREIGN KEY (target_evidence_id) REFERENCES evidence (id)
) COMMENT = '구매자 재촬영 요청 및 처리 이력';

-- listing_status_history — 매물 상태 전이 이력 (타임아웃·취소 이력 보존)
CREATE TABLE listing_status_history (
    id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '이력 고유 식별자',
    listing_id  BIGINT       NOT NULL COMMENT '대상 매물 ID (FK)',
    from_status VARCHAR(20)  NOT NULL COMMENT '이전 상태',
    to_status   VARCHAR(20)  NOT NULL COMMENT '변경 상태',
    reason      VARCHAR(200) NULL COMMENT '사유(숨김/제재/취소/타임아웃 등)',
    actor_id    BIGINT       NULL COMMENT '수행자 회원 ID. 시스템(타임아웃)이면 NULL',
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '변경 시각',
    PRIMARY KEY (id),
    KEY idx_history_listing (listing_id),
    CONSTRAINT fk_history_listing FOREIGN KEY (listing_id) REFERENCES listing (id)
) COMMENT = '매물 상태 전이 이력';
