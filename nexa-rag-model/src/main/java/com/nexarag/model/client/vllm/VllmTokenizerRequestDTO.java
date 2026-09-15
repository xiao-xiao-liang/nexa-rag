package com.nexarag.model.client.vllm;

/**
 * vLLM {@code /tokenize} 接口请求载荷。
 *
 * @param model 目标模型名称
 * @param prompt 待统计文本，仅在请求体中传输
 */
public record VllmTokenizerRequestDTO(String model, String prompt) {
}
