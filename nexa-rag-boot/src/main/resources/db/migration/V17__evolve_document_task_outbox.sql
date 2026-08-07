RENAME TABLE document_pipeline_outbox TO document_task_outbox;

ALTER TABLE document_task_outbox
    CHANGE COLUMN process_id operation_id VARCHAR(64) NULL COMMENT '任务操作版本ID',
    CHANGE COLUMN failure_reason publish_failure_reason VARCHAR(1024) NULL COMMENT '消息发布失败原因',
    ADD COLUMN parent_outbox_id BIGINT NULL COMMENT '父任务Outbox ID' AFTER document_id,
    ADD COLUMN task_type VARCHAR(64) NOT NULL DEFAULT 'PROCESS_DOCUMENT' COMMENT '任务类型' AFTER operation_id,
    ADD COLUMN task_status VARCHAR(32) NOT NULL DEFAULT 'NOT_TRACKED' COMMENT '任务最终状态' AFTER publish_status,
    ADD COLUMN consume_retry_count INT NOT NULL DEFAULT '0' COMMENT '消费者执行重试次数' AFTER publish_retry_count,
    ADD COLUMN task_completed_time DATETIME NULL COMMENT '任务最终完成时间' AFTER published_time,
    ADD COLUMN task_failure_reason VARCHAR(1024) NULL COMMENT '任务最终失败原因' AFTER publish_failure_reason,
    ADD KEY idx_document_task_outbox_parent (parent_outbox_id),
    ADD KEY idx_document_task_outbox_status (task_type, task_status, update_time);
