ALTER TABLE listing_checklist_item
    ADD COLUMN manual_model_name VARCHAR(100) NULL,
    ADD COLUMN manual_storage_capacity VARCHAR(30) NULL,
    ADD COLUMN manual_os_version VARCHAR(200) NULL,
    ADD COLUMN manual_cpu VARCHAR(100) NULL;
