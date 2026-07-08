package com.nexarag.retrieval.config;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexarag.common.exception.ServiceException;
import com.nexarag.document.dto.IndexConfigRequest;
import com.nexarag.document.dto.ProcessDocumentRequest;
import com.nexarag.document.entity.Document;
import com.nexarag.document.error.DocumentErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 索引配置解析器，负责从文档处理配置快照中读取索引开关并补齐默认值。
 */
@Component
@RequiredArgsConstructor
public class IndexConfigResolver {

    private final ObjectMapper objectMapper;
    private final RetrievalProperties retrievalProperties;

    /**
     * 解析文档索引配置。
     *
     * @param document 文档实体
     * @return 索引运行配置快照
     */
    public IndexConfigSnapshot resolve(Document document) {
        if (document == null || !StringUtils.hasText(document.getProcessConfigJson())) {
            return defaultSnapshot();
        }
        try {
            // 1. 读取文档处理配置快照
            ProcessDocumentRequest request = objectMapper.readValue(document.getProcessConfigJson(), ProcessDocumentRequest.class);
            IndexConfigRequest indexConfig = request.indexConfig();
            if (indexConfig == null) {
                return defaultSnapshot();
            }

            // 2. 对缺省字段应用默认索引策略
            boolean enabled = indexConfig.enabled() == null || indexConfig.enabled();
            boolean vectorEnabled = indexConfig.vectorEnabled() == null || indexConfig.vectorEnabled();
            boolean keywordEnabled = (indexConfig.keywordEnabled() == null || indexConfig.keywordEnabled())
                    && keywordMiddlewareEnabled();
            return new IndexConfigSnapshot(enabled, vectorEnabled, keywordEnabled,
                    null, defaultVectorCollection(), defaultKeywordIndexName());
        } catch (JsonProcessingException exception) {
            throw new ServiceException("解析文档索引配置失败，documentId=" + document.getDocumentId(), exception,
                    DocumentErrorCode.DOCUMENT_PROCESS_CONFIG_INVALID);
        }
    }

    private IndexConfigSnapshot defaultSnapshot() {
        return new IndexConfigSnapshot(true, true, keywordMiddlewareEnabled(), null,
                defaultVectorCollection(), defaultKeywordIndexName());
    }

    private String defaultVectorCollection() {
        String configuredName = retrievalProperties.getVector().getCollectionName();
        if (StringUtils.hasText(configuredName)) {
            return configuredName;
        }
        return "nexa_document_chunk";
    }

    private String defaultKeywordIndexName() {
        String configuredName = retrievalProperties.getKeyword().getIndexName();
        if (StringUtils.hasText(configuredName)) {
            return configuredName;
        }
        return "nexa_document_chunk";
    }

    private boolean keywordMiddlewareEnabled() {
        return !"none".equalsIgnoreCase(retrievalProperties.getKeyword().getType());
    }
}
