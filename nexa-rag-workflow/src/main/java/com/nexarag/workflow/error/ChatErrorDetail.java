package com.nexarag.workflow.error;

/**
 * 工作流错误解析结果。
 *
 * @param errorCode    错误码
 * @param errorMessage 精简友好的中文错误说明
 */
public record ChatErrorDetail(String errorCode, String errorMessage) {
}
