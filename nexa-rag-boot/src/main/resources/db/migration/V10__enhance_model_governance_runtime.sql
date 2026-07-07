ALTER TABLE model_governance_config
    MODIFY COLUMN config_id BIGINT NULL COMMENT '模型配置ID',
    ADD COLUMN binding_mode VARCHAR(32) NOT NULL DEFAULT 'CONFIG' COMMENT '治理配置绑定模式：CONFIG按模型配置绑定，ROUTE按路由key绑定' AFTER governance_id,
    ADD COLUMN route_key VARCHAR(128) NULL COMMENT '模型路由key，ROUTE绑定模式使用' AFTER config_id,
    ADD COLUMN time_limiter_enabled TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否启用同步调用超时保护' AFTER bulkhead_enabled,
    ADD COLUMN time_limiter_timeout_ms INT NOT NULL DEFAULT 60000 COMMENT '同步调用超时时间，单位毫秒' AFTER time_limiter_enabled,
    ADD COLUMN stream_first_chunk_timeout_ms INT NOT NULL DEFAULT 30000 COMMENT '流式调用首个分片超时时间，单位毫秒' AFTER time_limiter_timeout_ms,
    ADD COLUMN stream_max_duration_ms INT NOT NULL DEFAULT 300000 COMMENT '流式调用最大持续时间，单位毫秒' AFTER stream_first_chunk_timeout_ms;

ALTER TABLE model_call_log
    ADD COLUMN token_usage_source VARCHAR(32) NULL COMMENT 'Token用量统计来源' AFTER total_tokens,
    ADD COLUMN first_token_latency_ms BIGINT NULL COMMENT '首个Token或首个分片耗时，单位毫秒' AFTER duration_ms,
    ADD COLUMN chunk_count INT NULL COMMENT '流式响应分片数量' AFTER first_token_latency_ms,
    ADD COLUMN output_char_count INT NULL COMMENT '输出字符数' AFTER chunk_count,
    ADD COLUMN estimated_output_tokens INT NULL COMMENT '估算输出Token数量' AFTER output_char_count;

CREATE INDEX idx_model_governance_config_config
    ON model_governance_config (binding_mode, config_id, del_flag);

CREATE INDEX idx_model_governance_config_route
    ON model_governance_config (binding_mode, route_key, del_flag);
