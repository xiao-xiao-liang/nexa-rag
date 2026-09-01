UPDATE document_task_outbox dto
JOIN document_version dv ON dv.document_id = dto.document_id
    AND dv.revision_no = 1
SET dto.document_version_id = dv.document_version_id,
    dto.activation_generation = COALESCE(dto.activation_generation, 0)
WHERE dto.document_version_id IS NULL;
