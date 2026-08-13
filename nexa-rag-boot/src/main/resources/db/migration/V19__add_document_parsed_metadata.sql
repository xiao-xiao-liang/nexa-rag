ALTER TABLE document
    ADD COLUMN parsed_metadata_json JSON NULL COMMENT '解析附属制品与结构元数据' AFTER parsed_content_type;
