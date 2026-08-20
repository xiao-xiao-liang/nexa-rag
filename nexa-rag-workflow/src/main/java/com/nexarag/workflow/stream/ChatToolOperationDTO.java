package com.nexarag.workflow.stream;

/**
 * 面向客户端展示的工具调用最小投影，不包含参数、结果或内部异常。
 *
 * @param opId 工具调用稳定标识
 * @param processId 本次回答的工具流程标识
 * @param sequence 工具调用顺序
 * @param name 工具名称
 * @param status 工具展示状态
 */
public record ChatToolOperationDTO(String opId, String processId, long sequence,
                                   String name, ChatToolOperationStatus status) {
}
