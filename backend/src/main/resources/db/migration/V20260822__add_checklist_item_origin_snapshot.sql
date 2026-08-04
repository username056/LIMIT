-- 매물 체크리스트 항목의 출처(기본/선택 기능)를 생성 시점 스냅샷으로 고정한다.
-- 기존에는 "지금" 카테고리 표준 템플릿이나 카탈로그를 다시 조회해 기본 항목 여부를 추론했는데,
-- 등록 이후 표준 템플릿이 새 버전으로 개정되면 그 추론이 흔들려 기존 기본 항목이 선택 기능으로
-- 오인될 위험이 있었다. 이 항목에 한해서는 listing_checklist_item 자신이 유일한 판단 기준이 된다.
--
-- 기존 행은 전부 안전하게 BASE로 채운다 — 이 마이그레이션 이전에는 매물 수정 화면에서 confirmedFeatures를
-- 바꿀 방법 자체가 없었으므로, 실제로 선택 기능 항목이었던 기존 행이 있어도 "기본 항목처럼 삭제 대상에서
-- 제외됨"이라는 안전한 방향으로만 어긋난다(잘못 삭제되는 방향이 아니다).
--
-- (listing_id, item_code)/(checklist_template_id, item_code) 중복은 자동으로 지우지 않는다 —
-- 이 기능 이전에는 그런 중복을 만들 수 있는 코드 경로 자체가 없었고, 하필 중복 행에 증빙이 붙어
-- 있으면 삭제가 다른 FK를 조용히 위반하거나 운영자 모르게 데이터를 지우게 된다. 실제로 중복이
-- 있다면 아래 ADD CONSTRAINT가 MySQL 에러로 명확히 막아 운영자가 직접 판단하게 한다.

SET @migration_sql = (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE listing_checklist_item ADD COLUMN item_origin VARCHAR(30) NOT NULL DEFAULT ''BASE'' AFTER item_code',
        'SELECT 1')
    FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'listing_checklist_item' AND column_name = 'item_origin'
);
PREPARE migration_statement FROM @migration_sql;
EXECUTE migration_statement;
DEALLOCATE PREPARE migration_statement;

SET @migration_sql = (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE listing_checklist_item ADD COLUMN feature_code VARCHAR(30) NULL AFTER item_origin',
        'SELECT 1')
    FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'listing_checklist_item' AND column_name = 'feature_code'
);
PREPARE migration_statement FROM @migration_sql;
EXECUTE migration_statement;
DEALLOCATE PREPARE migration_statement;

SET @migration_sql = (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE listing_checklist_item ADD CONSTRAINT uk_listing_checklist_item_listing_item_code UNIQUE (listing_id, item_code)',
        'SELECT 1')
    FROM information_schema.statistics
    WHERE table_schema = DATABASE() AND table_name = 'listing_checklist_item'
      AND index_name = 'uk_listing_checklist_item_listing_item_code'
);
PREPARE migration_statement FROM @migration_sql;
EXECUTE migration_statement;
DEALLOCATE PREPARE migration_statement;

SET @migration_sql = (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE checklist_template_item ADD CONSTRAINT uk_checklist_template_item_template_item_code UNIQUE (checklist_template_id, item_code)',
        'SELECT 1')
    FROM information_schema.statistics
    WHERE table_schema = DATABASE() AND table_name = 'checklist_template_item'
      AND index_name = 'uk_checklist_template_item_template_item_code'
);
PREPARE migration_statement FROM @migration_sql;
EXECUTE migration_statement;
DEALLOCATE PREPARE migration_statement;
