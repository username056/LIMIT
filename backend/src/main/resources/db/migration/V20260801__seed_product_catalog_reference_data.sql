-- 상품 등록에 필요한 MVP 기준 데이터.
-- 기존 목업 계약의 4개 카테고리를 유지하고, 각 카테고리에서 실제 등록 가능한 모델,
-- PUBLISHED 체크리스트 템플릿, 판매 준비 가이드를 함께 제공한다.

INSERT INTO category (
    parent_id, name, device_type, manufacturer, manufacturer_id, os_family,
    model_code, supported_storage_gb, display_order, is_active
)
SELECT NULL, seed.name, seed.device_type, NULL, NULL, NULL,
       NULL, NULL, seed.display_order, b'1'
FROM (
    SELECT '일반형 스마트폰' AS name, 'SMARTPHONE' AS device_type, 1 AS display_order
    UNION ALL SELECT '폴더블 스마트폰', 'FOLDABLE', 2
    UNION ALL SELECT '태블릿', 'TABLET', 3
    UNION ALL SELECT 'Windows 노트북', 'LAPTOP', 4
) seed
WHERE NOT EXISTS (
    SELECT 1
    FROM category existing
    WHERE existing.parent_id IS NULL
      AND existing.name = seed.name
      AND existing.device_type = seed.device_type
);

INSERT INTO category (
    parent_id, name, device_type, manufacturer, manufacturer_id, os_family,
    model_code, supported_storage_gb, display_order, is_active
)
SELECT parent.id, seed.model_name, seed.device_type, seed.manufacturer,
       CRC32(LOWER(seed.manufacturer)), seed.os_family, seed.model_code,
       seed.supported_storage_gb, seed.display_order, b'1'
FROM (
    SELECT '일반형 스마트폰' AS parent_name, 'Galaxy S24' AS model_name,
           'SMARTPHONE' AS device_type, 'Samsung' AS manufacturer,
           'ANDROID' AS os_family, 'SM-S921N' AS model_code,
           '128,256,512' AS supported_storage_gb, 1 AS display_order
    UNION ALL
    SELECT '일반형 스마트폰', 'iPhone 15', 'SMARTPHONE', 'Apple',
           'IOS', 'A3090', '128,256,512', 2
    UNION ALL
    SELECT '폴더블 스마트폰', 'Galaxy Z Flip6', 'FOLDABLE', 'Samsung',
           'ANDROID', 'SM-F741N', '256,512', 1
    UNION ALL
    SELECT '태블릿', 'Galaxy Tab S9', 'TABLET', 'Samsung',
           'ANDROID', 'SM-X710N', '128,256', 1
    UNION ALL
    SELECT '태블릿', 'iPad Air 11 (M2)', 'TABLET', 'Apple',
           'IOS', 'A2902', '128,256,512,1024', 2
    UNION ALL
    SELECT 'Windows 노트북', 'Galaxy Book4', 'LAPTOP', 'Samsung',
           'WINDOWS', 'NT750XGK', '256,512,1024', 1
    UNION ALL
    SELECT 'Windows 노트북', 'LG gram 16', 'LAPTOP', 'LG',
           'WINDOWS', '16Z90S', '256,512,1024', 2
) seed
JOIN category parent
  ON parent.parent_id IS NULL
 AND parent.name = seed.parent_name
WHERE NOT EXISTS (
    SELECT 1
    FROM category existing
    WHERE existing.model_code = seed.model_code
);

INSERT INTO checklist_template (category_id, version, status, published_at, created_at)
SELECT model.id, 1, 'PUBLISHED', CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6)
FROM category model
WHERE model.model_code IN (
    'SM-S921N', 'A3090', 'SM-F741N', 'SM-X710N', 'A2902', 'NT750XGK', '16Z90S'
)
  AND NOT EXISTS (
      SELECT 1
      FROM checklist_template existing
      WHERE existing.category_id = model.id
        AND existing.version = 1
  );

INSERT INTO checklist_template_item (
    checklist_template_id, item_code, name, purpose, capture_guide,
    evidence_type, automation_type, parser_type, is_required, allowed_formats,
    min_count, max_count, min_duration_sec, max_duration_sec, max_file_size_mb,
    visible_to_buyer, privacy_masking_required, display_order
)
SELECT template.id, item.item_code, item.name, item.purpose, item.capture_guide,
       item.evidence_type, item.automation_type, item.parser_type, item.is_required,
       item.allowed_formats, item.min_count, item.max_count, item.min_duration_sec,
       item.max_duration_sec, item.max_file_size_mb, item.visible_to_buyer,
       item.privacy_masking_required, item.display_order
FROM checklist_template template
JOIN category model ON model.id = template.category_id
JOIN (
    SELECT 'EXT-001' AS item_code, '전면·후면·측면 외관' AS name,
           '찍힘·균열·변색 등 외관 상태 확인' AS purpose,
           '케이스를 제거하고 기기의 전면, 후면과 모든 측면을 밝은 곳에서 촬영하세요.' AS capture_guide,
           'PHOTO' AS evidence_type, 'NONE' AS automation_type, NULL AS parser_type,
           b'1' AS is_required, 'image/jpeg,image/png' AS allowed_formats,
           6 AS min_count, 6 AS max_count, NULL AS min_duration_sec,
           NULL AS max_duration_sec, 10 AS max_file_size_mb,
           b'1' AS visible_to_buyer, b'0' AS privacy_masking_required, 1 AS display_order
    UNION ALL
    SELECT 'DSP-002', '화면 전체 터치', '화면 표시와 터치 불량 확인',
           '화면 전체 격자를 끊김 없이 드래그하는 과정을 촬영하세요.',
           'VIDEO', 'NONE', NULL, b'1', 'video/mp4,video/webm',
           NULL, NULL, 15, 60, 100, b'1', b'0', 2
    UNION ALL
    SELECT 'SYS-003', '기기 정보 화면', '모델명·저장 용량·OS 버전 확인',
           '개인정보를 가린 뒤 기기 정보 화면 전체를 촬영하세요.',
           'PHOTO', 'OCR', 'DEVICE_INFO', b'1', 'image/jpeg,image/png',
           1, 1, NULL, NULL, 10, b'1', b'1', 3
    UNION ALL
    SELECT 'PRV-004', '계정 제거 및 초기화', '이전 사용자 계정과 개인정보 제거 확인',
           '계정 제거와 초기화를 마친 뒤 완료 여부를 확인하세요.',
           'SELLER_CONFIRMATION', 'NONE', NULL, b'1', NULL,
           NULL, NULL, NULL, NULL, NULL, b'0', b'1', 4
) item
WHERE template.version = 1
  AND template.status = 'PUBLISHED'
  AND model.model_code IN (
      'SM-S921N', 'A3090', 'SM-F741N', 'SM-X710N', 'A2902', 'NT750XGK', '16Z90S'
  )
  AND NOT EXISTS (
      SELECT 1
      FROM checklist_template_item existing
      WHERE existing.checklist_template_id = template.id
        AND existing.item_code = item.item_code
  );

INSERT INTO checklist_template_item (
    checklist_template_id, item_code, name, purpose, capture_guide,
    evidence_type, automation_type, parser_type, is_required, allowed_formats,
    min_count, max_count, min_duration_sec, max_duration_sec, max_file_size_mb,
    visible_to_buyer, privacy_masking_required, display_order
)
SELECT template.id, item.item_code, item.name, item.purpose, item.capture_guide,
       'DIAGNOSTIC_FILE', 'FILE_PARSE', item.parser_type, b'1', item.allowed_formats,
       1, 1, NULL, NULL, 20, b'1', b'0', item.display_order
FROM checklist_template template
JOIN category model ON model.id = template.category_id
JOIN (
    SELECT 'BAT-005' AS item_code, '배터리 리포트' AS name,
           '설계 용량·완전 충전 용량·사이클 수 확인' AS purpose,
           'Windows에서 생성한 batteryreport.html 파일을 등록하세요.' AS capture_guide,
           'BATTERY_REPORT' AS parser_type, 'text/html' AS allowed_formats, 5 AS display_order
    UNION ALL
    SELECT 'DXD-006', '시스템 진단 정보', 'CPU·메모리·GPU·드라이버 정보 확인',
           'dxdiag에서 모든 정보를 저장한 TXT 또는 XML 파일을 등록하세요.',
           'DXDIAG', 'text/plain,application/xml,text/xml', 6
) item
WHERE template.version = 1
  AND template.status = 'PUBLISHED'
  AND model.device_type = 'LAPTOP'
  AND model.model_code IN ('NT750XGK', '16Z90S')
  AND NOT EXISTS (
      SELECT 1
      FROM checklist_template_item existing
      WHERE existing.checklist_template_id = template.id
        AND existing.item_code = item.item_code
  );

INSERT INTO account_removal_guide (
    device_type, manufacturer, template_version, steps, disclaimer_text, created_at
)
SELECT seed.device_type, seed.manufacturer, 1, seed.steps,
       '서비스는 개인정보의 완전한 삭제를 보증하지 않습니다. 판매 전 계정 목록과 초기화 상태를 다시 확인하세요.',
       CURRENT_TIMESTAMP(6)
FROM (
    SELECT 'SMARTPHONE' AS device_type, 'Samsung' AS manufacturer,
           JSON_ARRAY('필요한 사진과 연락처를 백업하세요.', 'Google 계정을 제거하세요.', 'Samsung 계정에서 로그아웃하세요.', '공장 데이터 초기화를 실행하세요.') AS steps
    UNION ALL
    SELECT 'SMARTPHONE', 'Apple',
           JSON_ARRAY('필요한 데이터를 백업하세요.', '나의 찾기를 끄세요.', 'Apple 계정에서 로그아웃하세요.', '모든 콘텐츠 및 설정 지우기를 실행하세요.')
    UNION ALL
    SELECT 'FOLDABLE', 'Samsung',
           JSON_ARRAY('필요한 사진과 연락처를 백업하세요.', 'Google 계정을 제거하세요.', 'Samsung 계정에서 로그아웃하세요.', '공장 데이터 초기화를 실행하세요.')
    UNION ALL
    SELECT 'TABLET', 'Samsung',
           JSON_ARRAY('필요한 데이터를 백업하세요.', 'Google 계정을 제거하세요.', 'Samsung 계정에서 로그아웃하세요.', '공장 데이터 초기화를 실행하세요.')
    UNION ALL
    SELECT 'TABLET', 'Apple',
           JSON_ARRAY('필요한 데이터를 백업하세요.', '나의 찾기를 끄세요.', 'Apple 계정에서 로그아웃하세요.', '모든 콘텐츠 및 설정 지우기를 실행하세요.')
    UNION ALL
    SELECT 'LAPTOP', 'Samsung',
           JSON_ARRAY('필요한 파일을 백업하세요.', 'Microsoft 계정 연결을 해제하세요.', '브라우저와 앱의 로그인 정보를 삭제하세요.', 'Windows 초기화를 실행하세요.')
    UNION ALL
    SELECT 'LAPTOP', 'LG',
           JSON_ARRAY('필요한 파일을 백업하세요.', 'Microsoft 계정 연결을 해제하세요.', '브라우저와 앱의 로그인 정보를 삭제하세요.', 'Windows 초기화를 실행하세요.')
) seed
WHERE NOT EXISTS (
    SELECT 1
    FROM account_removal_guide existing
    WHERE existing.device_type = seed.device_type
      AND existing.manufacturer = seed.manufacturer
      AND existing.template_version = 1
);
