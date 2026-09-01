-- document 仅保留稳定身份、版本指针、业务归属和文档级删除审计；
-- 文件快照及处理生命周期统一由 document_version 承担。
ALTER TABLE document
    DROP INDEX idx_document_status,
    DROP INDEX idx_document_source_type,
    DROP INDEX idx_document_knowledge_base_status,
    DROP COLUMN original_file_name,
    DROP COLUMN file_type,
    DROP COLUMN file_size,
    DROP COLUMN original_file_url,
    DROP COLUMN original_object_name,
    DROP COLUMN source_type,
    DROP COLUMN source_url,
    DROP COLUMN parsed_file_url,
    DROP COLUMN parsed_object_name,
    DROP COLUMN parsed_content_type,
    DROP COLUMN parsed_metadata_json,
    DROP COLUMN status,
    DROP COLUMN process_id,
    DROP COLUMN message_status,
    DROP COLUMN consumed_times,
    DROP COLUMN last_message_id,
    DROP COLUMN queue_stage,
    DROP COLUMN queue_time,
    DROP COLUMN process_start_time,
    DROP COLUMN process_end_time,
    DROP COLUMN process_config_json,
    DROP COLUMN failure_stage,
    DROP COLUMN failure_reason,
    DROP COLUMN failure_detail,
    DROP COLUMN retry_count,
    DROP COLUMN max_retry_count,
    DROP COLUMN last_retry_time,
    DROP COLUMN cleanup_status,
    DROP COLUMN cleanup_retry_count,
    DROP COLUMN cleanup_failure_reason,
    ADD KEY idx_document_knowledge_base_del_flag (knowledge_base_id, del_flag);

-- V28 回填的旧文档终态为 INDEXED；版本状态机使用 INDEX_READY 表示索引预热完成。
-- 统一状态后，补偿 V30 在旧状态下未能完成的处理 Outbox。
UPDATE document_version
SET status = 'INDEX_READY',
    index_ready_time = COALESCE(index_ready_time, process_end_time, update_time, create_time),
    process_end_time = COALESCE(process_end_time, index_ready_time, update_time, create_time),
    failure_stage = NULL,
    failure_reason = NULL,
    failure_detail = NULL
WHERE status = 'INDEXED';

UPDATE document_task_outbox dto
    JOIN document_version dv ON dto.document_version_id = dv.document_version_id
    AND dto.operation_id = dv.process_id
    SET dto.task_status = 'SUCCEEDED',
        dto.task_completed_time = COALESCE(dto.task_completed_time, dv.process_end_time, dv.index_ready_time, NOW()),
        dto.task_failure_reason = NULL
WHERE dto.task_type = 'PROCESS_DOCUMENT'
  AND dto.publish_status = 'PUBLISHED'
  AND dto.task_status IN ('PENDING', 'PROCESSING')
  AND dv.status = 'INDEX_READY';