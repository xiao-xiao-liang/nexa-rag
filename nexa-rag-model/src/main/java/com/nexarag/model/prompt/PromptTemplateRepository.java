package com.nexarag.model.prompt;

import java.util.Optional;

/**
 * Prompt 模板仓储接口。
 */
public interface PromptTemplateRepository {

    /**
     * 根据模板 Key 查询模板。
     *
     * @param key 模板Key
     * @return Prompt 模板
     */
    Optional<PromptTemplate> findByKey(String key);
}
