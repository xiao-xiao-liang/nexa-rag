package com.nexarag.chat.domain;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

/**
 * 负责引用清单的版本化 JSON 编解码。
 */
@Component
public class ChatCitationSetCodec {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /**
     * 将当前版本引用清单编码为消息存储字段。
     *
     * @param citationSet 引用清单
     * @return JSON 文本
     */
    public String encode(ChatCitationSetDTO citationSet) {
        try {
            return OBJECT_MAPPER.writeValueAsString(citationSet == null
                    ? new ChatCitationSetDTO(ChatCitationSetDTO.CURRENT_VERSION, java.util.List.of())
                    : citationSet);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("引用清单序列化失败", exception);
        }
    }

    /**
     * 解码可兼容历史空值和未知版本的引用清单。
     *
     * @param json 存储的 JSON 文本
     * @return 当前可识别的引用清单
     */
    public ChatCitationSetDTO decode(String json) {
        if (json == null || json.isBlank()) {
            return emptySet();
        }
        try {
            ChatCitationSetDTO citationSet = OBJECT_MAPPER.readValue(json, ChatCitationSetDTO.class);
            if (citationSet == null || citationSet.version() != ChatCitationSetDTO.CURRENT_VERSION) {
                return emptySet();
            }
            return citationSet;
        } catch (JsonProcessingException exception) {
            return emptySet();
        }
    }

    private ChatCitationSetDTO emptySet() {
        return new ChatCitationSetDTO(ChatCitationSetDTO.CURRENT_VERSION, java.util.List.of());
    }
}
