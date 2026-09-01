UPDATE document_version
SET failure_stage = NULL,
    failure_reason = NULL,
    failure_detail = NULL
WHERE status = 'INDEX_READY'
  AND (failure_stage IS NOT NULL OR failure_reason IS NOT NULL OR failure_detail IS NOT NULL);
