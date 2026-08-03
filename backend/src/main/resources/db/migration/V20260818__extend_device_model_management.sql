-- 관리자 모델 관리에서 비활성화 이력과 대체 모델을 보존한다.
-- 모델은 매물, 옵션, 체크리스트 조사 이력에서 참조하므로 물리 삭제하지 않는다.

ALTER TABLE device_model
    ADD COLUMN disabled_at DATETIME(6) NULL AFTER review_note,
    ADD COLUMN disabled_by_admin_id BIGINT NULL AFTER disabled_at,
    ADD COLUMN disable_reason VARCHAR(500) NULL AFTER disabled_by_admin_id,
    ADD COLUMN replacement_model_id BIGINT NULL AFTER disable_reason,
    ADD CONSTRAINT fk_device_model_replacement
        FOREIGN KEY (replacement_model_id) REFERENCES device_model (model_id),
    ADD INDEX idx_device_model_category_active_updated
        (category_id, is_active, updated_at, model_id),
    ADD INDEX idx_device_model_review_updated
        (review_status, updated_at, model_id);

CREATE INDEX idx_listing_model_updated
    ON listing (device_model_id, updated_at, id);

CREATE INDEX idx_listing_model_created
    ON listing (device_model_id, created_at, id);

CREATE INDEX idx_model_research_model_updated
    ON model_checklist_research (device_model_id, updated_at, id);
