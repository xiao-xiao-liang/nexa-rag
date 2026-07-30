ALTER TABLE model_governance_config
    MODIFY COLUMN enabled TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否启用治理配置',
    MODIFY COLUMN retry_wait_ms INT NOT NULL DEFAULT 200 COMMENT '重试等待时间毫秒',
    MODIFY COLUMN circuit_enabled TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否启用熔断',
    MODIFY COLUMN slow_call_rate_threshold INT NOT NULL DEFAULT 50 COMMENT '慢调用比例阈值',
    MODIFY COLUMN slow_call_duration_ms INT NOT NULL DEFAULT 30000 COMMENT '慢调用判定时长毫秒',
    MODIFY COLUMN rate_limit_enabled TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否启用限流',
    MODIFY COLUMN limit_for_period INT NOT NULL DEFAULT 60 COMMENT '单周期允许调用数',
    MODIFY COLUMN limit_refresh_period_ms INT NOT NULL DEFAULT 60000 COMMENT '限流周期刷新时间毫秒',
    MODIFY COLUMN bulkhead_enabled TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否启用并发隔离',
    MODIFY COLUMN time_limiter_enabled TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否启用同步调用超时保护',
    MODIFY COLUMN max_concurrent_calls INT NOT NULL DEFAULT 8 COMMENT '最大并发调用数';
