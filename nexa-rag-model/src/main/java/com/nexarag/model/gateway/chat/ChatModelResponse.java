package com.nexarag.model.gateway.chat;

import lombok.Builder;

/**
 * 聊天模型响应。
 *
 * @param content          响应内容
 * @param modelProfile     实际使用的模型Profile
 * @param promptTokens     输入Token数量
 * @param completionTokens 输出Token数量
 * @param totalTokens      总Token数量
 */
@Builder
public record ChatModelResponse(String content, String modelProfile,
                                Integer promptTokens, Integer completionTokens, Integer totalTokens) {
}
