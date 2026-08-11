package com.nexarag.infra.source.yuque;

import com.alibaba.cloud.ai.parser.tika.TikaDocumentParser;
import com.alibaba.cloud.ai.reader.yuque.YuQueDocumentReader;
import com.alibaba.cloud.ai.reader.yuque.YuQueResource;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexarag.common.exception.ServiceException;
import com.nexarag.infra.config.YuqueSourceProperties;
import com.nexarag.infra.enums.ExternalDocumentSourceType;
import com.nexarag.infra.source.ExternalDocumentSourceReader;
import com.nexarag.infra.source.model.SourceReadRequestDTO;
import com.nexarag.infra.source.model.SourceReadResultBO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.util.List;
import java.util.Map;

/** 使用 Spring AI Alibaba Reader 读取单篇语雀文档。 */
@Component
@RequiredArgsConstructor
public class YuqueSourceReader implements ExternalDocumentSourceReader {

    private final YuqueSourceProperties properties;
    private final ObjectMapper objectMapper;

    @Override
    public boolean supports(ExternalDocumentSourceType sourceType) {
        return sourceType == ExternalDocumentSourceType.YUQUE;
    }

    @Override
    public String validateAndExtractDocumentId(String sourceUrl) {
        try {
            URI uri = URI.create(sourceUrl);
            String[] segments = uri.getPath().split("/");
            if (!"www.yuque.com".equalsIgnoreCase(uri.getHost()) || segments.length < 3
                    || segments[segments.length - 1].isBlank()) {
                throw new ServiceException("语雀来源URL必须指向单篇文档");
            }
            return segments[segments.length - 1];
        } catch (IllegalArgumentException exception) {
            throw new ServiceException("语雀来源URL格式错误");
        }
    }

    @Override
    public SourceReadResultBO read(SourceReadRequestDTO request) {
        if (!StringUtils.hasText(properties.getToken())) {
            throw new ServiceException("未配置语雀访问令牌");
        }
        validateAndExtractDocumentId(request.sourceUrl());
        List<org.springframework.ai.document.Document> documents = new YuQueDocumentReader(YuQueResource.builder()
                .yuQueToken(properties.getToken()).resourcePath(request.sourceUrl()).build(), new TikaDocumentParser()).get();
        String markdown = documents.stream().map(org.springframework.ai.document.Document::getText)
                .filter(StringUtils::hasText).reduce((left, right) -> left + "\n\n" + right)
                .orElseThrow(() -> new ServiceException("语雀文档未返回正文"));
        return new SourceReadResultBO(serialize(documents), "application/json", markdown, null,
                validateAndExtractDocumentId(request.sourceUrl()), null, Map.of("sourceType", "YUQUE"));
    }

    private byte[] serialize(List<org.springframework.ai.document.Document> documents) {
        try {
            return objectMapper.writeValueAsBytes(documents);
        } catch (JsonProcessingException exception) {
            throw new ServiceException("序列化语雀来源快照失败");
        }
    }
}
