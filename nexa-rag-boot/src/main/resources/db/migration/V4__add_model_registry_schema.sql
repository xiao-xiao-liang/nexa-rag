CREATE TABLE IF NOT EXISTS model_config (
    config_id BIGINT NOT NULL COMMENT '模型配置ID',
    config_key VARCHAR(128) NOT NULL COMMENT '模型配置唯一key',
    model_type VARCHAR(32) NOT NULL COMMENT '模型类型',
    provider VARCHAR(64) NOT NULL COMMENT '模型厂商',
    base_url VARCHAR(512) NOT NULL COMMENT '模型服务地址',
    api_key_cipher VARCHAR(1024) NULL COMMENT '加密后的API Key',
    api_key_mask VARCHAR(128) NULL COMMENT 'API Key脱敏值',
    model_name VARCHAR(128) NOT NULL COMMENT '模型名称',
    enabled TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否启用',
    timeout_ms INT NOT NULL DEFAULT 30000 COMMENT '超时时间毫秒',
    max_retries INT NOT NULL DEFAULT 0 COMMENT '最大重试次数',
    version BIGINT NOT NULL DEFAULT 1 COMMENT '单条配置版本',
    extra_config TEXT NULL COMMENT '扩展配置JSON',
    remark VARCHAR(512) NULL COMMENT '备注',
    create_time DATETIME NOT NULL COMMENT '创建时间',
    update_time DATETIME NOT NULL COMMENT '更新时间',
    del_flag TINYINT NOT NULL DEFAULT 0 COMMENT '删除标记：0未删除，1已删除',
    delete_time DATETIME NULL COMMENT '删除时间',
    PRIMARY KEY (config_id),
    UNIQUE KEY uk_model_config_key (config_key),
    KEY idx_model_config_type_provider (model_type, provider),
    KEY idx_model_config_del_flag (del_flag)
) COMMENT='模型配置表';

CREATE TABLE IF NOT EXISTS model_route (
    route_id BIGINT NOT NULL COMMENT '模型路由ID',
    route_key VARCHAR(128) NOT NULL COMMENT '模型路由唯一key',
    model_type VARCHAR(32) NOT NULL COMMENT '模型类型',
    strategy VARCHAR(32) NOT NULL COMMENT '路由策略',
    enabled TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否启用',
    remark VARCHAR(512) NULL COMMENT '备注',
    create_time DATETIME NOT NULL COMMENT '创建时间',
    update_time DATETIME NOT NULL COMMENT '更新时间',
    del_flag TINYINT NOT NULL DEFAULT 0 COMMENT '删除标记：0未删除，1已删除',
    delete_time DATETIME NULL COMMENT '删除时间',
    version INT NOT NULL DEFAULT 0 COMMENT '版本号',
    PRIMARY KEY (route_id),
    UNIQUE KEY uk_model_route_key (route_key),
    KEY idx_model_route_type (model_type),
    KEY idx_model_route_del_flag (del_flag)
) COMMENT='模型路由表';

CREATE TABLE IF NOT EXISTS model_route_config (
    route_config_id BIGINT NOT NULL COMMENT '模型路由配置关联ID',
    route_id BIGINT NOT NULL COMMENT '模型路由ID',
    config_id BIGINT NOT NULL COMMENT '模型配置ID',
    role VARCHAR(32) NOT NULL COMMENT '路由下模型配置角色',
    priority INT NOT NULL DEFAULT 0 COMMENT '优先级',
    weight INT NOT NULL DEFAULT 0 COMMENT '权重',
    enabled TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否启用',
    create_time DATETIME NOT NULL COMMENT '创建时间',
    update_time DATETIME NOT NULL COMMENT '更新时间',
    del_flag TINYINT NOT NULL DEFAULT 0 COMMENT '删除标记：0未删除，1已删除',
    delete_time DATETIME NULL COMMENT '删除时间',
    version INT NOT NULL DEFAULT 0 COMMENT '版本号',
    PRIMARY KEY (route_config_id),
    UNIQUE KEY uk_model_route_config (route_id, config_id, role),
    KEY idx_model_route_config_route_id (route_id),
    KEY idx_model_route_config_config_id (config_id),
    KEY idx_model_route_config_del_flag (del_flag)
) COMMENT='模型路由配置关联表';

CREATE TABLE IF NOT EXISTS model_registry_version (
    version_id BIGINT NOT NULL COMMENT '版本记录ID',
    version_no BIGINT NOT NULL COMMENT '全局版本号',
    update_time DATETIME NOT NULL COMMENT '更新时间',
    PRIMARY KEY (version_id)
) COMMENT='模型注册表版本表';

INSERT INTO model_registry_version (version_id, version_no, update_time)
SELECT 1, 1, NOW()
WHERE NOT EXISTS (
    SELECT 1 FROM model_registry_version WHERE version_id = 1
);
