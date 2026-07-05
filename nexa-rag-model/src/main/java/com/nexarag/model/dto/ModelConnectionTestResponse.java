package com.nexarag.model.dto;

import com.nexarag.model.enums.ModelProvider;
import com.nexarag.model.enums.ModelType;
import lombok.Builder;

/**
 * 模型连接测试响应。
 *
 * @param success         是否测试成功
 * @param provider        模型厂商
 * @param modelType       模型类型
 * @param modelName       模型名称
 * @param baseUrl         模型服务地址
 * @param durationMs      耗时，单位毫秒
 * @param vectorDimension 向量维度
 * @param rerankCount     重排序结果数量
 * @param errorCode       错误编码
 * @param errorMessage    错误消息
 */
@Builder
public record ModelConnectionTestResponse(boolean success, ModelProvider provider, ModelType modelType,
                                          String modelName, String baseUrl, long durationMs,
                                          Integer vectorDimension, Integer rerankCount,
                                          String errorCode, String errorMessage) {
}
