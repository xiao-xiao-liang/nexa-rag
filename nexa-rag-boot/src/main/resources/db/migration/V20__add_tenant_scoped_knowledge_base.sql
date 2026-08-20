CREATE TABLE IF NOT EXISTS knowledge_base (
    knowledge_base_id BIGINT NOT NULL COMMENT '知识库ID',
    tenant_id VARCHAR(64) NOT NULL COMMENT '租户ID',
    name VARCHAR(128) NOT NULL COMMENT '知识库名称',
    active_name_key VARCHAR(128) NULL COMMENT '有效名称规范键',
    description VARCHAR(1024) NULL COMMENT '知识库描述',
    is_default TINYINT NOT NULL DEFAULT 0 COMMENT '是否默认知识库：0否，1是',
    default_tenant_key VARCHAR(64) NULL COMMENT '默认库租户唯一键',
    create_time DATETIME NOT NULL COMMENT '创建时间',
    update_time DATETIME NOT NULL COMMENT '更新时间',
    create_by VARCHAR(64) NULL COMMENT '创建人',
    update_by VARCHAR(64) NULL COMMENT '更新人',
    del_flag TINYINT NOT NULL DEFAULT 0 COMMENT '删除标记：0未删除，1已删除',
    delete_time DATETIME NULL COMMENT '删除时间',
    version INT NOT NULL DEFAULT 0 COMMENT '版本号',
    PRIMARY KEY (knowledge_base_id),
    UNIQUE KEY uk_knowledge_base_tenant_active_name (tenant_id, active_name_key),
    UNIQUE KEY uk_knowledge_base_default_tenant (default_tenant_key),
    KEY idx_knowledge_base_tenant_update (tenant_id, del_flag, update_time)
) COMMENT='知识库表';

INSERT INTO knowledge_base (knowledge_base_id, tenant_id, name, active_name_key, description,
                            is_default, default_tenant_key, create_time, update_time, create_by, update_by,
                            del_flag, version)
SELECT 1, 'default-tenant', '默认知识库', '默认知识库', '系统初始化的默认知识库',
       1, 'default-tenant', NOW(), NOW(), 'system', 'system', 0, 0
WHERE NOT EXISTS (
    SELECT 1 FROM knowledge_base WHERE default_tenant_key = 'default-tenant'
);

ALTER TABLE document
    ADD COLUMN knowledge_base_id BIGINT NOT NULL COMMENT '所属知识库ID' AFTER document_id,
    ADD KEY idx_document_knowledge_base_status (knowledge_base_id, del_flag, status);
