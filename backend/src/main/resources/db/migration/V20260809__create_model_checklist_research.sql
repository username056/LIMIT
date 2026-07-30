CREATE TABLE model_checklist_research (
    id BIGINT NOT NULL AUTO_INCREMENT,
    category_id BIGINT NOT NULL,
    research_version INT NOT NULL,
    status VARCHAR(30) NOT NULL,
    result_json LONGTEXT NULL,
    published_template_id BIGINT NULL,
    reviewed_by_admin_id BIGINT NULL,
    review_note VARCHAR(500) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_model_checklist_research_category_version (category_id, research_version),
    INDEX idx_model_checklist_research_status_created (status, created_at),
    CONSTRAINT fk_model_checklist_research_category
        FOREIGN KEY (category_id) REFERENCES category (id),
    CONSTRAINT fk_model_checklist_research_template
        FOREIGN KEY (published_template_id) REFERENCES checklist_template (id)
);
