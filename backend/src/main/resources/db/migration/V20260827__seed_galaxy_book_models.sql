-- 판매하기의 Windows 노트북 선택지에 국내 출시 Galaxy Book2~Book5 계열을 보강한다.
-- 모델 코드는 삼성전자 국내 제품 SKU에서 옵션 접미사를 제외한 제품군 코드로 사용한다.
-- category -> device_model -> device_variant를 함께 만들고, 기존 Galaxy Book4의
-- PUBLISHED 체크리스트를 복제해 추가 직후에도 판매 등록이 가능하게 한다.

-- 1) category 리프
INSERT INTO category (
    parent_id, name, device_type, manufacturer, manufacturer_id, os_family,
    model_code, supported_storage_gb, display_order, is_active
)
SELECT parent.id, seed.model_name, 'LAPTOP', 'Samsung',
       CRC32(LOWER('Samsung')), 'WINDOWS', seed.model_code,
       seed.supported_storage_gb, seed.display_order, b'1'
FROM (
    SELECT 'Galaxy Book4 Pro' AS model_name, 'NT960XGK' AS model_code,
           '512,1024' AS supported_storage_gb, 8 AS display_order
    UNION ALL
    SELECT 'Galaxy Book4 Pro 360', 'NT960QGK', '512,1024', 9
    UNION ALL
    SELECT 'Galaxy Book4 Ultra', 'NT960XGL', '1024,2048', 10
    UNION ALL
    SELECT 'Galaxy Book4 Edge', 'NT960XMB', '1024', 11
    UNION ALL
    SELECT 'Galaxy Book5', 'NT750XHD', '256,512', 12
    UNION ALL
    SELECT 'Galaxy Book5 Pro', 'NT960XHA', '256,512,1024,2048', 13
    UNION ALL
    SELECT 'Galaxy Book5 Pro 360', 'NT960QHA', '512,1024,2048', 14
    UNION ALL
    SELECT 'Galaxy Book3', 'NT750XFG', '256,512', 15
    UNION ALL
    SELECT 'Galaxy Book3 360', 'NT750QFG', '256,512', 16
    UNION ALL
    SELECT 'Galaxy Book3 Pro 14', 'NT940XFG', '256,512,1024', 17
    UNION ALL
    SELECT 'Galaxy Book3 Pro 16', 'NT960XFG', '256,512,1024', 18
    UNION ALL
    SELECT 'Galaxy Book3 Pro 360', 'NT960QFG', '512,1024', 19
    UNION ALL
    SELECT 'Galaxy Book3 Ultra', 'NT960XFH', '512,1024', 20
    UNION ALL
    SELECT 'Galaxy Book2', 'NT550XEZ', '256,512', 21
    UNION ALL
    SELECT 'Galaxy Book2 360', 'NT750QED', '256,512', 22
    UNION ALL
    SELECT 'Galaxy Book2 Pro', 'NT950XED', '256,512,1024', 23
    UNION ALL
    SELECT 'Galaxy Book2 Pro 360', 'NT950QED', '256,512,1024', 24
) seed
JOIN category parent
  ON parent.parent_id IS NULL
 AND parent.name = 'Windows 노트북'
WHERE NOT EXISTS (
    SELECT 1 FROM category existing WHERE existing.model_code = seed.model_code
);

-- 2) device_model. 기존 카탈로그 이관 규칙과 같이 category 리프의 id를 보존한다.
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
       CASE
           WHEN c.model_code IN ('NT550XEZ', 'NT750QED', 'NT950XED', 'NT950QED') THEN 2022
           WHEN c.model_code IN (
               'NT750XFG', 'NT750QFG', 'NT940XFG', 'NT960XFG', 'NT960QFG', 'NT960XFH'
           ) THEN 2023
           WHEN c.model_code IN ('NT960XGK', 'NT960QGK', 'NT960XGL', 'NT960XMB') THEN 2024
           ELSE 2025
       END,
       c.is_active,
       c.display_order,
       'VERIFIED',
       'CATALOG',
       CURRENT_TIMESTAMP(6),
       CURRENT_TIMESTAMP(6)
FROM category c
WHERE c.model_code IN (
    'NT960XGK', 'NT960QGK', 'NT960XGL', 'NT960XMB',
    'NT750XHD', 'NT960XHA', 'NT960QHA',
    'NT750XFG', 'NT750QFG', 'NT940XFG', 'NT960XFG', 'NT960QFG', 'NT960XFH',
    'NT550XEZ', 'NT750QED', 'NT950XED', 'NT950QED'
)
  AND NOT EXISTS (
      SELECT 1 FROM device_model existing WHERE existing.model_id = c.id
  );

-- 3) 저장 용량별 device_variant
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
        'NT960XGK', 'NT960QGK', 'NT960XGL', 'NT960XMB',
        'NT750XHD', 'NT960XHA', 'NT960QHA',
        'NT750XFG', 'NT750QFG', 'NT940XFG', 'NT960XFG', 'NT960QFG', 'NT960XFH',
        'NT550XEZ', 'NT750QED', 'NT950XED', 'NT950QED'
    )
) expanded ON expanded.model_id = m.model_id
WHERE expanded.storage_gb > 0
  AND NOT EXISTS (
      SELECT 1
      FROM device_variant existing
      WHERE existing.model_id = m.model_id
        AND existing.variant_key = CONCAT(m.model_code, '-', expanded.storage_gb)
  );

-- 4) 판매 등록용 PUBLISHED 체크리스트 템플릿
INSERT INTO checklist_template (category_id, version, status, published_at, created_at)
SELECT model.id, 1, 'PUBLISHED', CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6)
FROM category model
JOIN category source_model
  ON source_model.model_code = 'NT750XGK'
WHERE model.model_code IN (
    'NT960XGK', 'NT960QGK', 'NT960XGL', 'NT960XMB',
    'NT750XHD', 'NT960XHA', 'NT960QHA',
    'NT750XFG', 'NT750QFG', 'NT940XFG', 'NT960XFG', 'NT960QFG', 'NT960XFH',
    'NT550XEZ', 'NT750QED', 'NT950XED', 'NT950QED'
)
  AND NOT EXISTS (
      SELECT 1
      FROM checklist_template existing
      WHERE existing.category_id = model.id
        AND existing.version = 1
  );

-- 5) 기존 Galaxy Book4 체크리스트 항목 복제
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
  ON source_model.model_code = 'NT750XGK'
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
    'NT960XGK', 'NT960QGK', 'NT960XGL', 'NT960XMB',
    'NT750XHD', 'NT960XHA', 'NT960QHA',
    'NT750XFG', 'NT750QFG', 'NT940XFG', 'NT960XFG', 'NT960QFG', 'NT960XFH',
    'NT550XEZ', 'NT750QED', 'NT950XED', 'NT950QED'
)
  AND NOT EXISTS (
      SELECT 1
      FROM checklist_template_item existing
      WHERE existing.checklist_template_id = target_template.id
        AND existing.item_code = item.item_code
  );
