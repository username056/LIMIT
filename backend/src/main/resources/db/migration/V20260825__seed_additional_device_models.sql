-- 판매하기 '기기 모델 선택' 드롭다운에 노출할 모델을 2026년 8월 기준 최신 15종으로 채운다.
-- 버전은 V20260824__add_member_profile_image.sql과 충돌하지 않도록 V20260825로 배치한다.
--
-- V20260801 시드 이후 나온 실제 판매 모델로 카테고리별 보강한다. category 리프 →
-- device_model → device_variant 3단을 함께 채워야 상품 등록이 FK 위반 없이 동작한다
-- (DeviceCatalogRegistrar 주석 참고: category에만 넣으면 그 모델로 등록이 안 된다).
--
-- V20260816의 AI 조사는 기존 PUBLISHED 템플릿에 사후 보강을 더하는 구조다. 따라서
-- 스마트폰·폴더블·태블릿 모델이 등록 플로우에서 CHECKLIST_TEMPLATE_NOT_FOUND가 되지 않도록
-- 동종 기존 모델의 템플릿과 항목을 복제한다. 노트북도 일관성을 위해 함께 복제한다.
-- account_removal_guide는 이 15종의 (device_type, manufacturer) 조합이 V20260801에 이미 있어 추가하지 않는다.
--
-- manufacturer_id는 기존 시드·이관과 동일하게 CRC32(LOWER(TRIM(manufacturer)))로 계산한다.
-- Samsung/Apple/LG는 이미 manufacturer 테이블에 있어 별도 INSERT가 필요 없다.
--
-- 참고: Galaxy Z Fold8 Ultra(SM-F976N)·Z Flip8(SM-F776N)는 2026-07-22 공개, 2026-08-07 정식
-- 출시로 이 마이그레이션 작성 시점(2026-08-05) 기준 아직 국내 출시 이틀 전이다. 모델 코드는
-- 공개된 해외 코드(SM-F976*, SM-F776*)에 기존 시드와 동일한 국내 접미사 N을 적용해 추정했으니
-- 정식 출시 후 국내 코드가 다르게 확정되면 이 마이그레이션을 정정하는 후속 migration으로 고칠 것.

-- 1) category 리프
INSERT INTO category (
    parent_id, name, device_type, manufacturer, manufacturer_id, os_family,
    model_code, supported_storage_gb, display_order, is_active
)
SELECT parent.id, seed.model_name, seed.device_type, seed.manufacturer,
       CRC32(LOWER(seed.manufacturer)), seed.os_family, seed.model_code,
       seed.supported_storage_gb, seed.display_order, b'1'
FROM (
    -- 일반형 스마트폰
    SELECT '일반형 스마트폰' AS parent_name, 'Galaxy S26' AS model_name,
           'SMARTPHONE' AS device_type, 'Samsung' AS manufacturer,
           'ANDROID' AS os_family, 'SM-S942N' AS model_code,
           '256,512' AS supported_storage_gb, 3 AS display_order
    UNION ALL
    SELECT '일반형 스마트폰', 'Galaxy S26 Ultra', 'SMARTPHONE', 'Samsung',
           'ANDROID', 'SM-S948N', '256,512,1024', 4
    UNION ALL
    SELECT '일반형 스마트폰', 'iPhone 17', 'SMARTPHONE', 'Apple',
           'IOS', 'A3520', '256,512', 5
    UNION ALL
    SELECT '일반형 스마트폰', 'iPhone 17 Pro Max', 'SMARTPHONE', 'Apple',
           'IOS', 'A3526', '256,512,1024,2048', 6
    -- 폴더블 스마트폰
    UNION ALL
    SELECT '폴더블 스마트폰', 'Galaxy Z Fold8 Ultra', 'FOLDABLE', 'Samsung',
           'ANDROID', 'SM-F976N', '256,512,1024', 2
    UNION ALL
    SELECT '폴더블 스마트폰', 'Galaxy Z Flip8', 'FOLDABLE', 'Samsung',
           'ANDROID', 'SM-F776N', '256,512', 3
    -- 태블릿
    UNION ALL
    SELECT '태블릿', 'Galaxy Tab S11 Ultra', 'TABLET', 'Samsung',
           'ANDROID', 'SM-X930N', '256,512,1024', 3
    UNION ALL
    SELECT '태블릿', 'Galaxy Tab S11', 'TABLET', 'Samsung',
           'ANDROID', 'SM-X730N', '128,256,512', 4
    UNION ALL
    SELECT '태블릿', 'iPad Pro 13 (M5)', 'TABLET', 'Apple',
           'IOS', 'A3360', '256,512,1024,2048', 5
    UNION ALL
    SELECT '태블릿', 'iPad Pro 11 (M5)', 'TABLET', 'Apple',
           'IOS', 'A3357', '256,512,1024,2048', 6
    -- Windows 노트북
    UNION ALL
    SELECT 'Windows 노트북', 'Galaxy Book6', 'LAPTOP', 'Samsung',
           'WINDOWS', 'NP740VJG', '256,512', 3
    UNION ALL
    SELECT 'Windows 노트북', 'Galaxy Book6 Pro', 'LAPTOP', 'Samsung',
           'WINDOWS', 'NP960XJG', '512,1024', 4
    UNION ALL
    SELECT 'Windows 노트북', 'Galaxy Book6 Ultra', 'LAPTOP', 'Samsung',
           'WINDOWS', 'NP960UJH', '512,1024,2048', 5
    UNION ALL
    SELECT 'Windows 노트북', 'LG gram Pro 17', 'LAPTOP', 'LG',
           'WINDOWS', '17Z90UR', '512,1024,2048', 6
    UNION ALL
    SELECT 'Windows 노트북', 'LG gram Pro 16', 'LAPTOP', 'LG',
           'WINDOWS', '16Z90U', '512,1024,2048', 7
) seed
JOIN category parent
  ON parent.parent_id IS NULL
 AND parent.name = seed.parent_name
WHERE NOT EXISTS (
    SELECT 1
    FROM category existing
    WHERE existing.model_code = seed.model_code
);

-- 2) device_model — ID는 방금 만든 category 리프의 id를 그대로 받는다(V20260813과 동일 규칙).
INSERT INTO device_model (
    model_id, category_id, manufacturer_id, model_name, normalized_model_name,
    model_code, os_family, release_year, is_active, display_order,
    review_status, source_type, created_at, updated_at
)
SELECT c.id,
       c.parent_id,
       CRC32(LOWER(TRIM(c.manufacturer))),
       c.name,
       LOWER(REPLACE(TRIM(c.name), ' ', '')),
       c.model_code,
       c.os_family,
       NULL,
       c.is_active,
       c.display_order,
       'VERIFIED',
       'CATALOG',
       CURRENT_TIMESTAMP(6),
       CURRENT_TIMESTAMP(6)
FROM category c
WHERE c.model_code IN (
    'SM-S942N', 'SM-S948N', 'A3520', 'A3526',
    'SM-F976N', 'SM-F776N',
    'SM-X930N', 'SM-X730N', 'A3360', 'A3357',
    'NP740VJG', 'NP960XJG', 'NP960UJH', '17Z90UR', '16Z90U'
)
  AND NOT EXISTS (
      SELECT 1 FROM device_model existing WHERE existing.model_id = c.id
  );

-- 3) device_variant — 저장 용량 축. 문자열을 행으로 전개하는 방식은 V20260813과 동일하다.
INSERT INTO device_variant (
    model_id, variant_key, display_name, storage_gb, is_active, created_at, updated_at
)
SELECT m.model_id,
       CONCAT(m.model_code, '-', expanded.storage_gb),
       CONCAT(m.model_name, ' ', expanded.storage_gb, 'GB'),
       expanded.storage_gb,
       b'1',
       CURRENT_TIMESTAMP(6),
       CURRENT_TIMESTAMP(6)
FROM device_model m
JOIN (
    SELECT DISTINCT
           c.id AS model_id,
           CAST(
               TRIM(SUBSTRING_INDEX(SUBSTRING_INDEX(c.supported_storage_gb, ',', seq.n), ',', -1))
               AS UNSIGNED
           ) AS storage_gb
    FROM category c
    JOIN (
        SELECT 1 AS n UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4
    ) seq
      ON seq.n <= CHAR_LENGTH(c.supported_storage_gb)
                  - CHAR_LENGTH(REPLACE(c.supported_storage_gb, ',', '')) + 1
    WHERE c.model_code IN (
        'SM-S942N', 'SM-S948N', 'A3520', 'A3526',
        'SM-F976N', 'SM-F776N',
        'SM-X930N', 'SM-X730N', 'A3360', 'A3357',
        'NP740VJG', 'NP960XJG', 'NP960UJH', '17Z90UR', '16Z90U'
    )
) expanded ON expanded.model_id = m.model_id
WHERE expanded.storage_gb > 0
  AND NOT EXISTS (
      SELECT 1
      FROM device_variant existing
      WHERE existing.model_id = m.model_id
        AND existing.variant_key = CONCAT(m.model_code, '-', expanded.storage_gb)
  );

-- 4) 판매 등록용 PUBLISHED checklist template
INSERT INTO checklist_template (category_id, version, status, published_at, created_at)
SELECT model.id, 1, 'PUBLISHED', CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6)
FROM category model
JOIN category source_model
  ON source_model.device_type = model.device_type
 AND source_model.model_code IN ('SM-S921N', 'SM-F741N', 'SM-X710N', 'NT750XGK')
WHERE model.model_code IN (
    'SM-S942N', 'SM-S948N', 'A3520', 'A3526',
    'SM-F976N', 'SM-F776N',
    'SM-X930N', 'SM-X730N', 'A3360', 'A3357',
    'NP740VJG', 'NP960XJG', 'NP960UJH', '17Z90UR', '16Z90U'
)
  AND NOT EXISTS (
      SELECT 1
      FROM checklist_template existing
      WHERE existing.category_id = model.id
        AND existing.version = 1
  );

-- 5) 동종 기존 모델의 checklist item 복제
INSERT INTO checklist_template_item (
    checklist_template_id, item_code, name, purpose, capture_guide,
    evidence_type, automation_type, parser_type, is_required, allowed_formats,
    min_count, max_count, min_duration_sec, max_duration_sec, max_file_size_mb,
    visible_to_buyer, privacy_masking_required, display_order
)
SELECT target_template.id, item.item_code, item.name, item.purpose, item.capture_guide,
       item.evidence_type, item.automation_type, item.parser_type, item.is_required,
       item.allowed_formats, item.min_count, item.max_count, item.min_duration_sec,
       item.max_duration_sec, item.max_file_size_mb, item.visible_to_buyer,
       item.privacy_masking_required, item.display_order
FROM category model
JOIN category source_model
  ON source_model.device_type = model.device_type
 AND source_model.model_code IN ('SM-S921N', 'SM-F741N', 'SM-X710N', 'NT750XGK')
JOIN checklist_template target_template
  ON target_template.category_id = model.id
 AND target_template.version = 1
 AND target_template.status = 'PUBLISHED'
JOIN checklist_template source_template
  ON source_template.category_id = source_model.id
 AND source_template.version = 1
 AND source_template.status = 'PUBLISHED'
JOIN checklist_template_item item
  ON item.checklist_template_id = source_template.id
WHERE model.model_code IN (
    'SM-S942N', 'SM-S948N', 'A3520', 'A3526',
    'SM-F976N', 'SM-F776N',
    'SM-X930N', 'SM-X730N', 'A3360', 'A3357',
    'NP740VJG', 'NP960XJG', 'NP960UJH', '17Z90UR', '16Z90U'
)
  AND NOT EXISTS (
      SELECT 1
      FROM checklist_template_item existing
      WHERE existing.checklist_template_id = target_template.id
        AND existing.item_code = item.item_code
  );
