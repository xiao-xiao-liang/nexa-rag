import type { ChatToolOperation } from "../../types";

/** 判断当前助手消息是否应展示任务执行卡。仅在真正发生工具调用时展示。 */
export function shouldShowToolExecutionBox(
  operations: ChatToolOperation[],
  _isGenerating?: boolean
): boolean {
  return operations && operations.length > 0;
}


/** 计算工具卡展示的任务状态文本。 */
export function getToolExecutionStatus(
  operations: ChatToolOperation[],
  messageStatus?: string
): string {
  if (messageStatus === "CANCELLED") return "任务已停止";
  if (messageStatus === "GENERATING" || operations.some((operation) => operation.status === "RUNNING")) {
    return "任务执行中";
  }
  if (operations.some((operation) => operation.status === "FAILED")) return "任务执行失败";
  return "任务执行完成";
}
