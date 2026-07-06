ALTER TABLE model_call_log
    ADD COLUMN attempt_no INT NOT NULL DEFAULT 1 COMMENT '第几次尝试' AFTER duration_ms,
    ADD COLUMN fallback_from_call_id VARCHAR(64) NULL COMMENT '降级来源调用ID' AFTER attempt_no,
    ADD COLUMN fallback_reason VARCHAR(128) NULL COMMENT '降级原因' AFTER fallback_from_call_id;
