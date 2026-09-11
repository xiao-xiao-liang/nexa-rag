package com.nexarag.chat.domain;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * 引用标题路径解析器，负责从分块元数据快照中提取展示用标题层级。
 */
@Component
public class ChatCitationHeadingPathResolver {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /**
     * 解析分块元数据中的标题路径。
     *
     * @param metadataJson 分块元数据 JSON
     * @return 保持原始顺序的非空标题路径；元数据不可用时返回空列表
     */
    public List<String> resolve(String metadataJson) {
        if (!StringUtils.hasText(metadataJson)) {
            return List.of();
        }
        try {
            JsonNode titlePath = OBJECT_MAPPER.readTree(metadataJson).path("titlePath");
            if (!titlePath.isArray()) {
                return List.of();
            }
            List<String> headings = new ArrayList<>();
            titlePath.forEach(node -> {
                String heading = node.asText();
                if (StringUtils.hasText(heading)) {
                    headings.add(heading);
                }
            });
            return List.copyOf(headings);
        } catch (JsonProcessingException exception) {
            return List.of();
        }
    }
}
