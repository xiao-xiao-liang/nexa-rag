CREATE TABLE IF NOT EXISTS document_section (
    section_id BIGINT NOT NULL COMMENT '章节ID',
    document_id BIGINT NOT NULL COMMENT '文档ID',
    parent_section_id BIGINT NULL COMMENT '父章节ID',
    title VARCHAR(512) NOT NULL COMMENT '章节标题',
    heading_path_json TEXT NOT NULL COMMENT '标题层级路径JSON',
    heading_level INT NOT NULL COMMENT '标题层级',
    start_line INT NOT NULL COMMENT '章节起始行号',
    end_line INT NOT NULL COMMENT '章节结束行号',
    create_time DATETIME NOT NULL COMMENT '创建时间',
    update_time DATETIME NOT NULL COMMENT '更新时间',
    del_flag TINYINT NOT NULL DEFAULT 0 COMMENT '删除标记：0未删除，1已删除',
    PRIMARY KEY (section_id),
    KEY idx_document_section_document (document_id),
    KEY idx_document_section_parent (document_id, parent_section_id)
) COMMENT='文档章节结构表';

ALTER TABLE document_chunk
    ADD COLUMN section_id BIGINT NULL COMMENT '所属章节ID' AFTER parent_chunk_id,
    ADD COLUMN index_content MEDIUMTEXT NULL COMMENT '用于索引的片段内容' AFTER text,
    ADD KEY idx_document_chunk_section (document_id, section_id);
