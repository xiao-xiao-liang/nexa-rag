package com.nexarag.model.prompt;

import com.nexarag.common.exception.ServiceException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * 本地 Markdown Prompt 模板仓储。
 */
@Slf4j
public class LocalPromptTemplateRepository implements PromptTemplateRepository {

    private final Map<String, PromptTemplate> templateMap = new HashMap<>();

    public LocalPromptTemplateRepository(String locationPattern) {
        loadTemplates(locationPattern);
    }

    @Override
    public Optional<PromptTemplate> findByKey(String key) {
        return Optional.ofNullable(templateMap.get(key));
    }

    private void loadTemplates(String locationPattern) {
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        try {
            Resource[] resources = resolver.getResources(locationPattern);
            for (Resource resource : resources) {
                String key = resolveKey(resource);
                String content = resource.getContentAsString(StandardCharsets.UTF_8);
                templateMap.put(key, new PromptTemplate(key, content));
                log.info("加载本地Prompt模板成功，key={}", key);
            }
        } catch (IOException exception) {
            throw new ServiceException("加载本地Prompt模板失败", exception, com.nexarag.common.error.BaseErrorCode.SERVICE_ERROR);
        }
    }

    private String resolveKey(Resource resource) throws IOException {
        String url = resource.getURL().toString().replace("\\", "/");
        int index = url.indexOf("/prompts/");
        if (index < 0) {
            throw new ServiceException("Prompt模板路径不合法: " + url);
        }

        // 1. 截取 prompts 后面的相对路径
        String relativePath = url.substring(index + "/prompts/".length());

        // 2. 去掉 .md 后缀，并将路径转换为模板 Key
        if (relativePath.endsWith(".md")) {
            relativePath = relativePath.substring(0, relativePath.length() - 3);
        }
        return relativePath.replace("/", ".");
    }
}
