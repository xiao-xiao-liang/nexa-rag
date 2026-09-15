START TRANSACTION;

-- 为最终回答的 USER 输入模板创建不可变版本并切换正式发布指针。
SELECT prompt_id
FROM prompt_definition
WHERE prompt_code = 'chat.answer.current-question'
FOR UPDATE;

SET @prompt_id = (SELECT prompt_id FROM prompt_definition
                  WHERE prompt_code = 'chat.answer.current-question');
SET @version_id = (SELECT COALESCE(MAX(version_id), 0) + 1 FROM prompt_version);
SET @release_id = (SELECT COALESCE(MAX(release_id), 0) + 1 FROM prompt_release);
SET @version_no = (SELECT COALESCE(MAX(version_no), 0) + 1 FROM prompt_version WHERE prompt_id = @prompt_id);
SET @release_revision = (SELECT current_release_revision + 1 FROM prompt_definition WHERE prompt_id = @prompt_id);

SET @content = '<current_question>\n{{question}}\n</current_question>';

INSERT INTO prompt_version (version_id, prompt_id, version_no, content, content_checksum, variable_schema_snapshot,
                            created_by, created_at, remark)
SELECT @version_id, prompt_id, @version_no, @content, SHA2(@content, 256), variable_schema,
       'SYSTEM', NOW(), '发布最终回答纯 USER 输入模板'
FROM prompt_definition
WHERE prompt_id = @prompt_id;

INSERT INTO prompt_release (release_id, prompt_id, stable_version_id, canary_version_id, canary_rule,
                            release_revision, released_by, released_at, rollback_from_release_id, remark)
VALUES (@release_id, @prompt_id, @version_id, NULL, NULL, @release_revision,
        'SYSTEM', NOW(), NULL, '发布最终回答纯 USER 输入模板');

UPDATE prompt_definition
SET current_release_id = @release_id,
    current_release_revision = @release_revision,
    update_time = NOW()
WHERE prompt_id = @prompt_id;

COMMIT;
