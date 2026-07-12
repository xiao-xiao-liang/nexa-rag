package com.nexarag.workflow.stream;

import com.nexarag.model.gateway.chat.ChatModelStreamResponse;

/**
 * Chat 生成结果累积器，线程安全地保存正文、Token 用量和结束原因。
 */
public class ChatGenerationAccumulator {

    private final StringBuilder content = new StringBuilder();
    private Integer promptTokens;
    private Integer completionTokens;
    private Integer totalTokens;
    private String finishReason;

    /**
     * 累积一个模型流分片。
     *
     * @param response 模型流分片
     */
    public synchronized void append(ChatModelStreamResponse response) {
        // 1. 累积非空正文
        if (response.content() != null) {
            content.append(response.content());
        }

        // 2. 保存模型返回的最新用量和结束原因
        promptTokens = latest(response.promptTokens(), promptTokens);
        completionTokens = latest(response.completionTokens(), completionTokens);
        totalTokens = latest(response.totalTokens(), totalTokens);
        finishReason = latest(response.finishReason(), finishReason);
    }

    /**
     * 获取当前不可变快照。
     *
     * @return 生成结果快照
     */
    public synchronized Snapshot snapshot() {
        return new Snapshot(content.toString(), promptTokens, completionTokens, totalTokens, finishReason);
    }

    private <T> T latest(T candidate, T current) {
        return candidate == null ? current : candidate;
    }

    /**
     * Chat 生成结果不可变快照。
     *
     * @param content 完整正文
     * @param promptTokens 输入 Token 数
     * @param completionTokens 输出 Token 数
     * @param totalTokens 总 Token 数
     * @param finishReason 结束原因
     */
    public record Snapshot(String content, Integer promptTokens, Integer completionTokens,
                           Integer totalTokens, String finishReason) {
    }
}
