ALTER TABLE document
    ADD COLUMN process_id VARCHAR(64) NULL COMMENT '文档处理流水号' AFTER status,
    ADD COLUMN message_status VARCHAR(32) NULL COMMENT '文档流水线消息状态' AFTER process_id,
    ADD COLUMN consumed_times INT NOT NULL DEFAULT 0 COMMENT '消息消费次数' AFTER message_status,
    ADD COLUMN last_message_id VARCHAR(128) NULL COMMENT '最近消费消息ID' AFTER consumed_times;

CREATE TABLE IF NOT EXISTS document_pipeline_outbox (
    outbox_id BIGINT NOT NULL COMMENT 'Outbox记录ID',
    document_id BIGINT NOT NULL COMMENT '文档ID',
    process_id VARCHAR(64) NOT NULL COMMENT '文档处理流水号',
    message_key VARCHAR(128) NOT NULL COMMENT '消息唯一键',
    topic VARCHAR(255) NOT NULL COMMENT '消息主题',
    message_body TEXT NOT NULL COMMENT '消息内容',
    publish_status VARCHAR(32) NOT NULL COMMENT '发布状态',
    publish_retry_count INT NOT NULL DEFAULT 0 COMMENT '发布重试次数',
    next_retry_time DATETIME NULL COMMENT '下次重试时间',
    lock_owner VARCHAR(128) NULL COMMENT '锁持有者',
    lock_time DATETIME NULL COMMENT '加锁时间',
    published_time DATETIME NULL COMMENT '发布时间',
    failure_reason VARCHAR(512) NULL COMMENT '失败原因',
    create_time DATETIME NOT NULL COMMENT '创建时间',
    update_time DATETIME NOT NULL COMMENT '更新时间',
    PRIMARY KEY (outbox_id),
    UNIQUE KEY uk_document_pipeline_outbox_message_key (message_key),
    KEY idx_document_pipeline_outbox_publish_retry (publish_status, next_retry_time)
) COMMENT='文档流水线消息Outbox表';
