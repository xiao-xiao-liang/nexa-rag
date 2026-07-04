ALTER TABLE document
    ADD COLUMN original_object_name VARCHAR(1024) NULL COMMENT '原始文件对象名' AFTER original_file_url,
    ADD COLUMN parsed_object_name VARCHAR(1024) NULL COMMENT '解析后文件对象名' AFTER parsed_file_url,
    ADD COLUMN parsed_content_type VARCHAR(128) NULL COMMENT '解析后内容类型' AFTER parsed_object_name;
