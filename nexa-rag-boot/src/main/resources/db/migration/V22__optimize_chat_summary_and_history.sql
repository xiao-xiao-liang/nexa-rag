ALTER TABLE chat_conversation_summary
    ADD COLUMN summary_until_sequence BIGINT NULL COMMENT '摘要覆盖的最后消息序号' AFTER last_message_id;

UPDATE chat_conversation_summary summary
JOIN chat_message message
    ON message.message_id = summary.last_message_id
    AND message.conversation_id = summary.conversation_id
    AND message.user_id = summary.user_id
    AND message.del_flag = 0
SET summary.summary_until_sequence = message.sequence
WHERE summary.del_flag = 0
  AND summary.summary_until_sequence IS NULL;
