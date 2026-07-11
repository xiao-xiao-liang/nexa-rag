ALTER TABLE document
    ADD COLUMN process_id VARCHAR(64) NULL COMMENT '当前文档处理轮次ID' AFTER status,
    ADD COLUMN message_status VARCHAR(32) NULL COMMENT '当前处理轮次消息状态' AFTER process_id,
    ADD COLUMN consumed_times INT NOT NULL DEFAULT 0 COMMENT '当前处理轮次已消费次数' AFTER message_status,
    ADD COLUMN last_message_id VARCHAR(128) NULL COMMENT '最近一次消费的RocketMQ消息ID' AFTER consumed_times;

CREATE TABLE IF NOT EXISTS document_pipeline_outbox (
    outbox_id BIGINT NOT NULL COMMENT 'Outbox记录ID',
    document_id BIGINT NOT NULL COMMENT '文档ID',
    process_id VARCHAR(64) NOT NULL COMMENT '文档处理流水号',
    message_key VARCHAR(128) NOT NULL COMMENT '消息唯一键',
    topic VARCHAR(128) NOT NULL COMMENT '消息主题',
    message_body TEXT NOT NULL COMMENT '消息内容',
    publish_status VARCHAR(32) NOT NULL COMMENT '发布状态',
    publish_retry_count INT NOT NULL DEFAULT 0 COMMENT '发布重试次数',
    next_retry_time DATETIME NULL COMMENT '下次重试时间',
    lock_owner VARCHAR(128) NULL COMMENT '锁持有者',
    lock_time DATETIME NULL COMMENT '加锁时间',
    published_time DATETIME NULL COMMENT '发布时间',
    failure_reason VARCHAR(1024) NULL COMMENT '失败原因',
    create_time DATETIME NOT NULL COMMENT '创建时间',
    update_time DATETIME NOT NULL COMMENT '更新时间',
    -- 主键：Outbox记录ID
    PRIMARY KEY (outbox_id),
    -- 唯一索引：保证消息唯一键不重复
    UNIQUE KEY uk_document_pipeline_outbox_message_key (message_key),
    -- 任务索引：按发布状态和下次重试时间扫描待发布任务
    KEY idx_document_pipeline_outbox_publish_task (publish_status, next_retry_time)
) COMMENT='文档流水线消息Outbox表';
