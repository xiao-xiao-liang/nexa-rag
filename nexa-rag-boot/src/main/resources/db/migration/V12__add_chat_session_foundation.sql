CREATE TABLE IF NOT EXISTS chat_conversation (
    conversation_id VARCHAR(64) NOT NULL COMMENT '会话ID',
    user_id VARCHAR(64) NOT NULL COMMENT '用户ID',
    title VARCHAR(256) NOT NULL COMMENT '会话标题',
    status VARCHAR(32) NOT NULL COMMENT '会话状态',
    last_message_id VARCHAR(64) NULL COMMENT '最近一条消息ID',
    last_message_time DATETIME NULL COMMENT '最近一条消息时间',
    version INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    create_time DATETIME NOT NULL COMMENT '创建时间',
    update_time DATETIME NOT NULL COMMENT '更新时间',
    del_flag TINYINT NOT NULL DEFAULT 0 COMMENT '删除标记：0未删除，1已删除',
    PRIMARY KEY (conversation_id),
    KEY idx_chat_conversation_user_status_time (user_id, status, update_time),
    KEY idx_chat_conversation_user_id (user_id)
) COMMENT='聊天会话';

CREATE TABLE IF NOT EXISTS chat_message (
    message_id VARCHAR(64) NOT NULL COMMENT '消息ID',
    conversation_id VARCHAR(64) NOT NULL COMMENT '会话ID',
    user_id VARCHAR(64) NOT NULL COMMENT '用户ID',
    sequence BIGINT NOT NULL COMMENT '会话内消息序号',
    role VARCHAR(32) NOT NULL COMMENT '消息角色',
    status VARCHAR(32) NOT NULL COMMENT '消息状态',
    content MEDIUMTEXT NULL COMMENT '消息正文',
    thinking_content MEDIUMTEXT NULL COMMENT '思考内容',
    references_json TEXT NULL COMMENT '引用信息JSON',
    prompt_tokens INT NULL COMMENT '输入Token数',
    completion_tokens INT NULL COMMENT '输出Token数',
    total_tokens INT NULL COMMENT '总Token数',
    failure_code VARCHAR(128) NULL COMMENT '失败编码',
    failure_message VARCHAR(512) NULL COMMENT '失败信息',
    create_time DATETIME NOT NULL COMMENT '创建时间',
    update_time DATETIME NOT NULL COMMENT '更新时间',
    del_flag TINYINT NOT NULL DEFAULT 0 COMMENT '删除标记：0未删除，1已删除',
    PRIMARY KEY (message_id),
    UNIQUE KEY uk_chat_message_conversation_sequence (conversation_id, sequence),
    KEY idx_chat_message_conversation_sequence (conversation_id, sequence),
    KEY idx_chat_message_conversation_status_sequence (conversation_id, status, sequence),
    KEY idx_chat_message_user_conversation_sequence (user_id, conversation_id, sequence)
) COMMENT='聊天消息';

CREATE TABLE IF NOT EXISTS chat_conversation_summary (
    summary_id VARCHAR(64) NOT NULL COMMENT '摘要ID',
    conversation_id VARCHAR(64) NOT NULL COMMENT '会话ID',
    user_id VARCHAR(64) NOT NULL COMMENT '用户ID',
    content TEXT NOT NULL COMMENT '摘要内容',
    last_message_id VARCHAR(64) NOT NULL COMMENT '摘要覆盖的最后消息ID',
    summary_version BIGINT NOT NULL COMMENT '摘要版本',
    create_time DATETIME NOT NULL COMMENT '创建时间',
    update_time DATETIME NOT NULL COMMENT '更新时间',
    del_flag TINYINT NOT NULL DEFAULT 0 COMMENT '删除标记：0未删除，1已删除',
    PRIMARY KEY (summary_id),
    UNIQUE KEY uk_chat_summary_conversation_version (conversation_id, summary_version),
    KEY idx_chat_summary_conversation_version (conversation_id, summary_version),
    KEY idx_chat_summary_user_conversation_version (user_id, conversation_id, summary_version)
) COMMENT='聊天会话摘要';
