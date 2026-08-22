package com.nexarag.chat.domain;

import java.util.List;

/**
 * 助手消息的版本化引用清单，用于持久化到 references_json。
 *
 * @param version   引用清单结构版本
 * @param citations 消息内按编号排序的引用
 */
public record ChatCitationSetDTO(int version, List<ChatCitationDTO> citations) {

    /** 当前引用清单结构版本。 */
    public static final int CURRENT_VERSION = 1;

    public ChatCitationSetDTO {
        citations = citations == null ? List.of() : List.copyOf(citations);
    }
}
