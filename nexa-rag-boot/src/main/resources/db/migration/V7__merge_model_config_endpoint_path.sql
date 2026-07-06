-- 1. 兼容旧库：如果 model_config 尚未包含 endpoint_path，则先补齐字段。
SET @endpoint_path_column_count = (
    SELECT COUNT(1)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'model_config'
      AND COLUMN_NAME = 'endpoint_path'
);
SET @add_endpoint_path_sql = IF(
        @endpoint_path_column_count = 0,
        'ALTER TABLE model_config ADD COLUMN endpoint_path VARCHAR(256) NULL COMMENT ''模型接口路径'' AFTER base_url',
        'SELECT 1'
                             );
PREPARE add_endpoint_path_stmt FROM @add_endpoint_path_sql;
EXECUTE add_endpoint_path_stmt;
DEALLOCATE PREPARE add_endpoint_path_stmt;

-- 2. 兼容旧库：如果历史 Chat 路径字段存在，则迁移已有自定义路径。
SET @completions_path_column_count = (
    SELECT COUNT(1)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'model_config'
      AND COLUMN_NAME = 'completions_path'
);
SET @copy_completions_path_sql = IF(
        @completions_path_column_count > 0,
        'UPDATE model_config SET endpoint_path = completions_path WHERE model_type = ''CHAT'' AND (endpoint_path IS NULL OR endpoint_path = '''') AND completions_path IS NOT NULL AND completions_path <> ''''',
        'SELECT 1'
                                 );
PREPARE copy_completions_path_stmt FROM @copy_completions_path_sql;
EXECUTE copy_completions_path_stmt;
DEALLOCATE PREPARE copy_completions_path_stmt;

-- 3. 兼容旧库：如果历史 Embedding 路径字段存在，则迁移已有自定义路径。
SET @embedding_path_column_count = (
    SELECT COUNT(1)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'model_config'
      AND COLUMN_NAME = 'embedding_path'
);
SET @copy_embedding_path_sql = IF(
        @embedding_path_column_count > 0,
        'UPDATE model_config SET endpoint_path = embedding_path WHERE model_type = ''EMBEDDING'' AND (endpoint_path IS NULL OR endpoint_path = '''') AND embedding_path IS NOT NULL AND embedding_path <> ''''',
        'SELECT 1'
                               );
PREPARE copy_embedding_path_stmt FROM @copy_embedding_path_sql;
EXECUTE copy_embedding_path_stmt;
DEALLOCATE PREPARE copy_embedding_path_stmt;

-- 4. 为未配置路径的模型写入默认接口路径。
UPDATE model_config
SET endpoint_path = '/chat/completions'
WHERE model_type = 'CHAT'
  AND (endpoint_path IS NULL OR endpoint_path = '');

UPDATE model_config
SET endpoint_path = '/embeddings'
WHERE model_type = 'EMBEDDING'
  AND (endpoint_path IS NULL OR endpoint_path = '');

UPDATE model_config
SET endpoint_path = '/compatible-api/v1/reranks'
WHERE model_type = 'RERANK'
  AND model_name = 'qwen3-rerank'
  AND (endpoint_path IS NULL OR endpoint_path = '');

UPDATE model_config
SET endpoint_path = '/services/rerank/text-rerank/text-rerank'
WHERE model_type = 'RERANK'
  AND (endpoint_path IS NULL OR endpoint_path = '');
