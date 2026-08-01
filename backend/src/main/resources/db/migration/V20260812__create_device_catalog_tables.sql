-- 기기 카탈로그 정식 테이블을 추가한다.
--
-- 지금은 category 한 테이블이 카테고리(최상위)와 기기 모델(리프)을 겸하고 있어, 모델에만 필요한
-- 열(model_code, supported_storage_gb)이 카테고리 행에서는 항상 NULL이고 색상·용량 조합은
-- 표현할 방법이 아예 없다. 카테고리/제조사/모델/판매옵션을 각자의 테이블로 분리한다.
--
-- category 테이블은 이 마이그레이션에서 건드리지 않는다. checklist_template과 listing이 그 행을
-- FK로 물고 있어 한 번에 끊을 수 없다. V20260813에서 데이터를 복제 이관하고 두 구조를 당분간
-- 병행한 뒤, 프론트 전환이 끝난 다음 단계에서 제거한다.

-- 최상위 기기 카테고리. code는 기존 category.device_type 값을 그대로 쓴다.
CREATE TABLE device_category (
    category_id BIGINT NOT NULL AUTO_INCREMENT,
    code VARCHAR(30) NOT NULL,
    name VARCHAR(100) NOT NULL,
    display_order INT NOT NULL DEFAULT 0,
    is_active BIT(1) NOT NULL DEFAULT b'1',
    PRIMARY KEY (category_id),
    UNIQUE KEY uk_device_category_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 제조사.
--
-- manufacturer_id는 AUTO_INCREMENT가 아니라 CRC32(lower(trim(name))) 값을 그대로 PK로 쓴다.
-- 기존 category.manufacturer_id가 이미 같은 방식으로 계산된 값이고, GET /api/v1/device-models의
-- manufacturerId 파라미터로 프론트에 이미 노출돼 있다. 새 시퀀스를 쓰면 이관 시점에 그 값이 전부
-- 바뀌어 기존 클라이언트의 필터가 조용히 깨진다.
--
-- 서로 다른 이름이 같은 CRC32를 갖는 경우 PK 충돌로 INSERT가 실패한다. 조용히 병합되는 것보다
-- 낫고, 그때 별도 채번으로 교체한다.
CREATE TABLE manufacturer (
    manufacturer_id BIGINT NOT NULL,
    name VARCHAR(50) NOT NULL,
    normalized_name VARCHAR(50) NOT NULL,
    is_active BIT(1) NOT NULL DEFAULT b'1',
    PRIMARY KEY (manufacturer_id),
    UNIQUE KEY uk_manufacturer_normalized_name (normalized_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 기기 모델.
--
-- manufacturer_id를 NULL 허용으로 둔다. V20260806이 시드한 '기타 (직접 입력)' 모델은 제조사가
-- 정해지지 않은 상태로 존재해야 하고(실제 제조사는 listing.custom_manufacturer에 매물별로 남는다),
-- 이 행을 제외하면 카탈로그에 없는 기기의 등록 경로가 사라진다.
--
-- 유니크 제약은 (manufacturer_id, model_code)다. 기획의 '동일 제조사·모델 코드 중복 금지'에
-- 해당한다. model_code 단독 유니크는 쓰지 않는다 — 제조사가 다르면 같은 코드가 존재할 수 있다.
CREATE TABLE device_model (
    model_id BIGINT NOT NULL AUTO_INCREMENT,
    category_id BIGINT NOT NULL,
    manufacturer_id BIGINT NULL,
    model_name VARCHAR(100) NOT NULL,
    normalized_model_name VARCHAR(100) NOT NULL,
    model_code VARCHAR(50) NOT NULL,
    os_family VARCHAR(30) NULL,
    release_year SMALLINT NULL,
    is_active BIT(1) NOT NULL DEFAULT b'1',
    display_order INT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (model_id),
    CONSTRAINT fk_device_model_category
        FOREIGN KEY (category_id) REFERENCES device_category (category_id),
    CONSTRAINT fk_device_model_manufacturer
        FOREIGN KEY (manufacturer_id) REFERENCES manufacturer (manufacturer_id),
    UNIQUE KEY uk_device_model_manufacturer_code (manufacturer_id, model_code),
    INDEX idx_device_model_category_active (category_id, is_active),
    INDEX idx_device_model_normalized_name (normalized_model_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 실제 판매되는 SKU 조합.
--
-- 기획서의 device_variant는 variant_key/display_name만 두지만, 등록 화면의 단계별 옵션 노출
-- ('색상을 고르면 그 색상에서 가능한 용량만')은 조합을 축별로 조회할 수 있어야 성립한다.
-- 그래서 선택 축을 열로 함께 보관하고 variant_key는 조합의 식별자로만 쓴다.
--
-- 축 열이 전부 NULL 허용인 이유: 카테고리마다 의미 있는 축이 다르다(노트북은 cpu/gpu/memory_gb,
-- 스마트폰은 color/storage_gb). 카테고리별로 테이블을 쪼개면 조인이 카테고리 수만큼 늘어난다.
CREATE TABLE device_variant (
    variant_id BIGINT NOT NULL AUTO_INCREMENT,
    model_id BIGINT NOT NULL,
    variant_key VARCHAR(120) NOT NULL,
    display_name VARCHAR(150) NOT NULL,
    color VARCHAR(50) NULL,
    storage_gb INT NULL,
    memory_gb INT NULL,
    screen_size_inches DECIMAL(4,2) NULL,
    cpu VARCHAR(100) NULL,
    gpu VARCHAR(100) NULL,
    weight_kg DECIMAL(5,3) NULL,
    connectivity VARCHAR(20) NULL,
    is_active BIT(1) NOT NULL DEFAULT b'1',
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (variant_id),
    CONSTRAINT fk_device_variant_model
        FOREIGN KEY (model_id) REFERENCES device_model (model_id),
    UNIQUE KEY uk_device_variant_model_key (model_id, variant_key),
    INDEX idx_device_variant_model_active (model_id, is_active)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
