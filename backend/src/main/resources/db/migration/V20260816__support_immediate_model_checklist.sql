-- 사용자가 요청한 모델을 즉시 카탈로그에 등록하고, 관리자 검토와 AI 재조사를
-- 판매 등록의 사후 절차로 전환한다.

ALTER TABLE device_model
    ADD COLUMN review_status VARCHAR(30) NOT NULL DEFAULT 'VERIFIED' AFTER display_order,
    ADD COLUMN source_type VARCHAR(30) NOT NULL DEFAULT 'CATALOG' AFTER review_status,
    ADD COLUMN reported_by_member_id BIGINT NULL AFTER source_type,
    ADD COLUMN reviewed_by_admin_id BIGINT NULL AFTER reported_by_member_id,
    ADD COLUMN reviewed_at DATETIME(6) NULL AFTER reviewed_by_admin_id,
    ADD COLUMN review_note VARCHAR(500) NULL AFTER reviewed_at,
    ADD INDEX idx_device_model_review_status_created (review_status, created_at);

ALTER TABLE device_model_request
    ADD COLUMN resolved_model_id BIGINT NULL AFTER resolved_category_id,
    ADD COLUMN provisioned_at DATETIME(6) NULL AFTER resolved_model_id;

UPDATE device_model_request
   SET resolved_model_id = resolved_category_id
 WHERE resolved_category_id IS NOT NULL
   AND resolved_model_id IS NULL;

ALTER TABLE device_model_request
    ADD CONSTRAINT fk_device_model_request_resolved_model
        FOREIGN KEY (resolved_model_id) REFERENCES device_model (model_id),
    ADD INDEX idx_device_model_request_resolved_model (resolved_model_id);

-- V20260812~13에서 category 리프와 device_model의 ID를 동일하게 이관했으므로
-- 기존 category_id 값은 그대로 정식 device_model FK로 전환할 수 있다.
ALTER TABLE model_checklist_research
    DROP FOREIGN KEY fk_model_checklist_research_category,
    DROP INDEX uk_model_checklist_research_category_version,
    CHANGE COLUMN category_id device_model_id BIGINT NOT NULL,
    ADD COLUMN input_snapshot_json LONGTEXT NULL AFTER status,
    ADD CONSTRAINT fk_model_checklist_research_model
        FOREIGN KEY (device_model_id) REFERENCES device_model (model_id),
    ADD UNIQUE KEY uk_model_checklist_research_model_version
        (device_model_id, research_version);
