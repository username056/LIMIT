ALTER TABLE listing
    MODIFY COLUMN price BIGINT NOT NULL,
    ADD COLUMN draft_step INT NOT NULL DEFAULT 1 AFTER precheck_completed;
