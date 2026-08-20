ALTER TABLE chat_message
    ADD COLUMN generation_id VARCHAR(64) NULL COMMENT '生成任务ID' AFTER status,
    ADD COLUMN tool_operations_json MEDIUMTEXT NULL COMMENT '工具运行卡终态快照JSON' AFTER references_json,
    ADD KEY idx_chat_message_generation_id (generation_id);
