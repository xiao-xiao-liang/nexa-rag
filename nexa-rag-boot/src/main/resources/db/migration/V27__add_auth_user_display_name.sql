ALTER TABLE auth_user
    ADD COLUMN display_name VARCHAR(128) NULL COMMENT '第三方登录返回的原始展示名称' AFTER account_name;
