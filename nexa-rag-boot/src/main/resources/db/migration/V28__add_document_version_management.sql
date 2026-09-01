ALTER TABLE document
    ADD COLUMN active_version_id BIGINT NULL COMMENT '当前生效文档版本ID' AFTER document_id,
    ADD COLUMN building_version_id BIGINT NULL COMMENT '正在构建的文档版本ID' AFTER active_version_id,
    ADD COLUMN activation_generation BIGINT NOT NULL DEFAULT 0 COMMENT '生效代次' AFTER building_version_id,
    ADD KEY idx_document_active_version (active_version_id),
    ADD KEY idx_document_building_version (building_version_id);

CREATE TABLE IF NOT EXISTS document_version (
    document_version_id BIGINT NOT NULL COMMENT '文档版本ID',
    document_id BIGINT NOT NULL COMMENT '文档ID',
    revision_no BIGINT NOT NULL COMMENT '文档内版本号',
    original_file_name VARCHAR(512) NOT NULL COMMENT '原始文件名',
    file_type VARCHAR(32) NOT NULL COMMENT '文件类型',
    file_size BIGINT NULL COMMENT '文件大小',
    original_file_url VARCHAR(1024) NULL COMMENT '原始文件地址',
    original_object_name VARCHAR(1024) NULL COMMENT '原始文件对象名',
    source_type VARCHAR(32) NOT NULL DEFAULT 'LOCAL' COMMENT '文档来源类型',
    source_url VARCHAR(1024) NULL COMMENT '外部来源URL',
    parsed_file_url VARCHAR(1024) NULL COMMENT '解析后文件地址',
    parsed_object_name VARCHAR(1024) NULL COMMENT '解析后文件对象名',
    parsed_content_type VARCHAR(128) NULL COMMENT '解析后内容类型',
    parsed_metadata_json JSON NULL COMMENT '解析附属制品与结构元数据',
    status VARCHAR(32) NOT NULL COMMENT '版本处理状态',
    process_id VARCHAR(64) NULL COMMENT '当前版本处理轮次ID',
    message_status VARCHAR(32) NULL COMMENT '当前处理轮次消息状态',
    consumed_times INT NOT NULL DEFAULT 0 COMMENT '当前处理轮次已消费次数',
    last_message_id VARCHAR(128) NULL COMMENT '最近一次消费的RocketMQ消息ID',
    queue_stage VARCHAR(32) NULL COMMENT '排队阶段',
    queue_time DATETIME NULL COMMENT '排队时间',
    process_start_time DATETIME NULL COMMENT '处理开始时间',
    process_end_time DATETIME NULL COMMENT '处理结束时间',
    process_config_json TEXT NULL COMMENT '处理配置快照',
    failure_stage VARCHAR(64) NULL COMMENT '失败阶段',
    failure_reason VARCHAR(512) NULL COMMENT '失败原因',
    failure_detail TEXT NULL COMMENT '失败详情',
    retry_count INT NOT NULL DEFAULT 0 COMMENT '已重试次数',
    max_retry_count INT NOT NULL DEFAULT 3 COMMENT '最大重试次数',
    last_retry_time DATETIME NULL COMMENT '最近重试时间',
    index_ready_time DATETIME NULL COMMENT '索引预热完成时间',
    cleanup_status VARCHAR(32) NULL COMMENT '清理状态',
    cleanup_retry_count INT NOT NULL DEFAULT 0 COMMENT '清理重试次数',
    cleanup_failure_reason VARCHAR(512) NULL COMMENT '清理失败原因',
    create_time DATETIME NOT NULL COMMENT '创建时间',
    update_time DATETIME NOT NULL COMMENT '更新时间',
    create_by VARCHAR(64) NULL COMMENT '创建人',
    update_by VARCHAR(64) NULL COMMENT '更新人',
    version INT NOT NULL DEFAULT 0 COMMENT '版本号',
    PRIMARY KEY (document_version_id),
    UNIQUE KEY uk_document_version_document_revision (document_id, revision_no),
    KEY idx_document_version_document_status (document_id, status),
    KEY idx_document_version_document_process (document_id, process_id)
) COMMENT='文档不可变版本表';

CREATE TABLE IF NOT EXISTS document_version_operation_log (
    operation_log_id BIGINT NOT NULL COMMENT '版本操作审计ID',
    document_id BIGINT NOT NULL COMMENT '文档ID',
    document_version_id BIGINT NULL COMMENT '文档版本ID',
    operation_type VARCHAR(32) NOT NULL COMMENT '操作类型',
    activation_generation BIGINT NULL COMMENT '生效代次',
    operator_id VARCHAR(64) NULL COMMENT '操作者ID',
    operation_detail TEXT NULL COMMENT '操作详情',
    create_time DATETIME NOT NULL COMMENT '操作时间',
    PRIMARY KEY (operation_log_id),
    KEY idx_document_version_operation_document_time (document_id, create_time),
    KEY idx_document_version_operation_version_time (document_version_id, create_time)
) COMMENT='文档版本操作审计表';

ALTER TABLE document_chunk
    ADD COLUMN document_version_id BIGINT NULL COMMENT '所属文档版本ID' AFTER document_id,
    ADD KEY idx_document_chunk_version (document_id, document_version_id);

ALTER TABLE document_section
    ADD COLUMN document_version_id BIGINT NULL COMMENT '所属文档版本ID' AFTER document_id,
    ADD KEY idx_document_section_version (document_id, document_version_id);

ALTER TABLE document_task_outbox
    ADD COLUMN document_version_id BIGINT NULL COMMENT '文档版本ID' AFTER document_id,
    ADD COLUMN activation_generation BIGINT NULL COMMENT '生效代次' AFTER operation_id,
    ADD KEY idx_document_task_outbox_version (document_id, document_version_id, task_type, task_status);

INSERT INTO document_version (
    document_version_id, document_id, revision_no, original_file_name, file_type, file_size,
    original_file_url, original_object_name, source_type, source_url, parsed_file_url,
    parsed_object_name, parsed_content_type, parsed_metadata_json, status, process_id,
    message_status, consumed_times, last_message_id, queue_stage, queue_time, process_start_time,
    process_end_time, process_config_json, failure_stage, failure_reason, failure_detail,
    retry_count, max_retry_count, last_retry_time, cleanup_status, cleanup_retry_count,
    cleanup_failure_reason, create_time, update_time, create_by, update_by, version
)
SELECT d.document_id, d.document_id, 1, d.original_file_name, d.file_type, d.file_size,
       d.original_file_url, d.original_object_name, d.source_type, d.source_url, d.parsed_file_url,
       d.parsed_object_name, d.parsed_content_type, d.parsed_metadata_json, d.status, d.process_id,
       d.message_status, d.consumed_times, d.last_message_id, d.queue_stage, d.queue_time,
       d.process_start_time, d.process_end_time, d.process_config_json, d.failure_stage,
       d.failure_reason, d.failure_detail, d.retry_count, d.max_retry_count, d.last_retry_time,
       d.cleanup_status, d.cleanup_retry_count, d.cleanup_failure_reason, d.create_time,
       d.update_time, d.create_by, d.update_by, d.version
FROM document d
WHERE NOT EXISTS (
    SELECT 1
    FROM document_version dv
    WHERE dv.document_id = d.document_id
      AND dv.revision_no = 1
);

UPDATE document d
JOIN document_version dv ON dv.document_id = d.document_id AND dv.revision_no = 1
SET d.active_version_id = dv.document_version_id
WHERE d.active_version_id IS NULL;

UPDATE document_chunk dc
JOIN document_version dv ON dv.document_id = dc.document_id AND dv.revision_no = 1
SET dc.document_version_id = dv.document_version_id
WHERE dc.document_version_id IS NULL;

UPDATE document_section ds
JOIN document_version dv ON dv.document_id = ds.document_id AND dv.revision_no = 1
SET ds.document_version_id = dv.document_version_id
WHERE ds.document_version_id IS NULL;
