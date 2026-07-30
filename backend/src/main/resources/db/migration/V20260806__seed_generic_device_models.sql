-- 카테고리별 '기타 (직접 입력)' 기기 모델과 체크리스트 템플릿을 시드한다.
--
-- 기존 시드에는 모델이 7종뿐이어서, 그 목록에 없는 기기를 가진 판매자는 상품을 등록할 방법이
-- 아예 없었다. 기기 모델은 category 테이블의 실제 행이고 checklist_template이 그 행에 묶여
-- 있으므로, 자유 입력을 받으려면 받아 줄 모델 행이 먼저 있어야 한다.
--
-- 세부 모델명은 판매자가 글제목에 적는다. listing에 별도 열을 두지 않아 스키마 변경(ALTER)이
-- 없고, 기존 등록·검수 파이프라인(카테고리 기준 템플릿 조회 → 매물 항목 스냅샷)을 그대로 탄다.
--
-- os_family는 비워 둔다. WINDOWS/LINUX로 두면 프론트가 AI 자동 생성 체크리스트 경로를 타는데,
-- 어떤 기기인지 모르는 상태에서는 근거 없는 항목이 생성될 수 있다. 기본 정책 항목만 쓴다.

INSERT INTO category (
    parent_id, name, device_type, manufacturer, manufacturer_id, os_family,
    model_code, supported_storage_gb, display_order, is_active
)
SELECT parent.id, '기타 (직접 입력)', parent.device_type, NULL, NULL, NULL,
       seed.model_code, NULL, 99, b'1'
FROM (
    SELECT '일반형 스마트폰' AS parent_name, 'ETC-SMARTPHONE' AS model_code
    UNION ALL SELECT '폴더블 스마트폰', 'ETC-FOLDABLE'
    UNION ALL SELECT '태블릿', 'ETC-TABLET'
    UNION ALL SELECT 'Windows 노트북', 'ETC-LAPTOP'
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
WHERE model.model_code IN ('ETC-SMARTPHONE', 'ETC-FOLDABLE', 'ETC-TABLET', 'ETC-LAPTOP')
  AND NOT EXISTS (
      SELECT 1
      FROM checklist_template existing
      WHERE existing.category_id = model.id
        AND existing.version = 1
  );

-- 기기 종류를 특정할 수 없으므로 모든 기기에 공통으로 요구하는 4개 항목만 둔다.
-- 노트북 전용 진단 파일(배터리 리포트·dxdiag)은 실제 기기가 노트북인지 보장할 수 없어 제외한다.
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
  AND model.model_code IN ('ETC-SMARTPHONE', 'ETC-FOLDABLE', 'ETC-TABLET', 'ETC-LAPTOP')
  AND NOT EXISTS (
      SELECT 1
      FROM checklist_template_item existing
      WHERE existing.checklist_template_id = template.id
        AND existing.item_code = item.item_code
  );
