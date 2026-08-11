ALTER TABLE document
    ADD COLUMN source_type VARCHAR(32) NOT NULL DEFAULT 'LOCAL' COMMENT '文档来源类型' AFTER original_object_name,
    ADD COLUMN source_url VARCHAR(1024) NULL COMMENT '外部来源URL' AFTER source_type,
    ADD KEY idx_document_source_type (source_type);
