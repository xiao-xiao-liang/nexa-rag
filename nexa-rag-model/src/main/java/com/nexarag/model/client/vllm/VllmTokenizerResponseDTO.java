package com.nexarag.model.client.vllm;

/**
 * vLLM {@code /tokenize} 接口响应载荷。
 *
 * @param count Token 数量
 */
public record VllmTokenizerResponseDTO(Integer count) {
}
