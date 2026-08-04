ALTER TABLE dxdiag_result
    ADD COLUMN model_name VARCHAR(100) NULL AFTER evidence_id,
    ADD COLUMN os_version VARCHAR(200) NULL AFTER model_name,
    ADD COLUMN storage_capacity VARCHAR(30) NULL AFTER os_version;
