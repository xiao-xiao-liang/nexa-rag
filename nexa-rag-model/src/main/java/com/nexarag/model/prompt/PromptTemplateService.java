package com.nexarag.model.prompt;

import com.nexarag.common.exception.ServiceException;
import com.samskivert.mustache.Mustache;

import java.util.Map;

/**
 * Prompt 模板服务。
 */
public class PromptTemplateService {

    private final PromptTemplateRepository repository;

    public PromptTemplateService(PromptTemplateRepository repository) {
        this.repository = repository;
    }

    /**
     * 渲染 Prompt 模板。
     *
     * @param key       模板Key
     * @param variables 模板变量
     * @return 渲染后的 Prompt
     */
    public String render(String key, Map<String, Object> variables) {
        PromptTemplate template = repository.findByKey(key)
                .orElseThrow(() -> new ServiceException("Prompt模板不存在: " + key));

        // 1. 编译 Mustache 模板
        var compiledTemplate = Mustache.compiler().compile(template.content());

        // 2. 使用变量渲染 Prompt
        return compiledTemplate.execute(variables);
    }
}
