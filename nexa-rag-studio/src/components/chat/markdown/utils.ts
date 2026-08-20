/**
 * 飞书多维表格消息与 Markdown 数据解析工具
 */

export interface ParsedFeishuMessage {
  isStructuredJson: boolean;
  thinkingContent?: string;
  toolCalls?: Array<{
    name: string;
    arguments?: string;
    title?: string;
    result?: string[];
  }>;
  markdownContent: string;
  status?: string;
}

/**
 * 智能解析消息文本：
 * 兼容纯 Markdown 文本与飞书官方带有 `ops` 的复杂 JSON 格式
 */
export function parseFeishuMessageContent(rawContent: string): ParsedFeishuMessage {
  if (!rawContent || typeof rawContent !== "string") {
    return {
      isStructuredJson: false,
      markdownContent: "",
    };
  }

  const trimmed = rawContent.trim();
  // 快速判断是否可能是 JSON 对象
  if (
    (trimmed.startsWith("{") && trimmed.endsWith("}")) ||
    (trimmed.startsWith("[") && trimmed.endsWith("]"))
  ) {
    try {
      const parsed = JSON.parse(trimmed);

      // 1. 处理最外层包含 { code, msg, data: { messages: [...] } } 的格式
      if (parsed.data?.messages && Array.isArray(parsed.data.messages)) {
        const latestMsgObj = parsed.data.messages[parsed.data.messages.length - 1];
        if (latestMsgObj?.message) {
          return parseFeishuMessageContent(latestMsgObj.message);
        }
      }

      // 2. 处理标准飞书 message 对象 { content, ops, status, ... }
      if (parsed.content !== undefined || Array.isArray(parsed.ops)) {
        let thinkingText = "";
        const tools: ParsedFeishuMessage["toolCalls"] = [];
        let finalAnswer = parsed.content || "";

        if (Array.isArray(parsed.ops)) {
          for (const op of parsed.ops) {
            if (op.type === "MARKDOWN_BLOCK" && op.data?.contentType === "thinking") {
              thinkingText = op.data.content || "";
            } else if (op.type === "TOOL_CALL" && op.data) {
              tools.push({
                name: op.data.name,
                arguments: op.data.arguments,
                title: op.data.name,
              });
            } else if (op.type === "TABLE_TOOL_CALL_PREVIEW" && op.data?.toolCalls) {
              for (const tc of op.data.toolCalls) {
                tools.push({
                  name: tc.functionName || tc.title,
                  title: tc.title,
                  result: tc.result,
                });
              }
            } else if (op.type === "COMMON_ANSWER" && op.data?.text) {
              finalAnswer = op.data.text;
            }
          }
        }

        return {
          isStructuredJson: true,
          thinkingContent: thinkingText || undefined,
          toolCalls: tools.length > 0 ? tools : undefined,
          markdownContent: finalAnswer,
          status: parsed.status,
        };
      }
    } catch {
      // JSON 解析失败，回退为普通 Markdown
    }
  }

  return {
    isStructuredJson: false,
    markdownContent: rawContent,
  };
}
