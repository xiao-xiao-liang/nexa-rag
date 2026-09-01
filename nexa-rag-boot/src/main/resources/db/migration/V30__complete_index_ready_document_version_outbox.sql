UPDATE document_task_outbox dto
JOIN document_version dv ON dv.document_id = dto.document_id
    AND dto.document_version_id = dv.document_version_id
    AND dto.operation_id = dv.process_id
SET dto.task_status = 'SUCCEEDED',
    dto.task_completed_time = COALESCE(dto.task_completed_time, dv.process_end_time, dv.index_ready_time, NOW()),
    dto.task_failure_reason = NULL
WHERE dto.task_type = 'PROCESS_DOCUMENT'
  AND dto.publish_status = 'PUBLISHED'
  AND dto.task_status IN ('PENDING', 'PROCESSING')
  AND dv.status = 'INDEX_READY';
