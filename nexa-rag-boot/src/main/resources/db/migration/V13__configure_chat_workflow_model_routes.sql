SET @chat_primary_config_id = (
    SELECT config_id FROM model_config
    WHERE model_type = 'CHAT' AND enabled = 1 AND del_flag = 0
    ORDER BY config_id LIMIT 1
);
SET @chat_backup_config_id = (
    SELECT config_id FROM model_config
    WHERE model_type = 'CHAT' AND enabled = 1 AND del_flag = 0
      AND config_id <> @chat_primary_config_id
    ORDER BY config_id LIMIT 1
);

INSERT INTO model_route (route_id, route_key, model_type, strategy, enabled, remark,
                         create_time, update_time, del_flag, version)
VALUES (1301, 'chat-answer', 'CHAT', 'PRIMARY_BACKUP', 1, 'Chat 最终回答路由', NOW(), NOW(), 0, 0),
       (1302, 'chat-rewrite', 'CHAT', 'PRIMARY_BACKUP', 1, 'Chat 问题改写路由', NOW(), NOW(), 0, 0),
       (1303, 'chat-intent', 'CHAT', 'PRIMARY_BACKUP', 1, 'Chat 意图识别路由', NOW(), NOW(), 0, 0),
       (1304, 'chat-summary', 'CHAT', 'PRIMARY_BACKUP', 1, 'Chat 摘要生成路由', NOW(), NOW(), 0, 0),
       (1305, 'chat-title', 'CHAT', 'PRIMARY_BACKUP', 1, 'Chat 标题生成路由', NOW(), NOW(), 0, 0)
ON DUPLICATE KEY UPDATE model_type = VALUES(model_type), strategy = VALUES(strategy), enabled = 1,
                        remark = VALUES(remark), update_time = NOW(), del_flag = 0, delete_time = NULL;

INSERT INTO model_route_config (route_config_id, route_id, config_id, role, priority, weight,
                                enabled, create_time, update_time, del_flag, version)
SELECT 13100 + route_id - 1300, route_id, @chat_primary_config_id, 'PRIMARY', 100, 100,
       1, NOW(), NOW(), 0, 0
FROM model_route
WHERE route_key IN ('chat-answer', 'chat-rewrite', 'chat-intent', 'chat-summary', 'chat-title')
  AND @chat_primary_config_id IS NOT NULL
ON DUPLICATE KEY UPDATE priority = 100, weight = 100, enabled = 1,
                        update_time = NOW(), del_flag = 0, delete_time = NULL;

INSERT INTO model_route_config (route_config_id, route_id, config_id, role, priority, weight,
                                enabled, create_time, update_time, del_flag, version)
SELECT 13200 + route_id - 1300, route_id, @chat_backup_config_id, 'BACKUP', 90, 100,
       1, NOW(), NOW(), 0, 0
FROM model_route
WHERE route_key IN ('chat-answer', 'chat-rewrite', 'chat-intent', 'chat-summary', 'chat-title')
  AND @chat_backup_config_id IS NOT NULL
ON DUPLICATE KEY UPDATE priority = 90, weight = 100, enabled = 1,
                        update_time = NOW(), del_flag = 0, delete_time = NULL;

INSERT INTO model_governance_config (
    governance_id, binding_mode, config_id, route_key, enabled,
    retry_enabled, max_attempts, retry_wait_ms,
    circuit_enabled, failure_rate_threshold, slow_call_rate_threshold,
    slow_call_duration_ms, minimum_number_of_calls, sliding_window_size,
    wait_duration_in_open_state_ms, rate_limit_enabled, limit_for_period,
    limit_refresh_period_ms, timeout_duration_ms, bulkhead_enabled,
    max_concurrent_calls, max_wait_duration_ms, time_limiter_enabled,
    time_limiter_timeout_ms, stream_first_chunk_timeout_ms, stream_max_duration_ms,
    create_time, update_time, del_flag
)
SELECT 2300 + route_id - 1300, 'ROUTE', NULL, route_key, 1,
       1, 2, 100, 1, 50, 100, 3000, 10, 20, 30000,
       1, 100, 1000, 0, 1, 20, 0, 1, 60000, 30000, 300000,
       NOW(), NOW(), 0
FROM model_route
WHERE route_key IN ('chat-answer', 'chat-rewrite', 'chat-intent', 'chat-summary', 'chat-title')
ON DUPLICATE KEY UPDATE binding_mode = 'ROUTE', route_key = VALUES(route_key), enabled = 1,
                        update_time = NOW(), del_flag = 0, delete_time = NULL;

UPDATE model_registry_version
SET version_no = version_no + 1, update_time = NOW()
WHERE version_id = 1;
