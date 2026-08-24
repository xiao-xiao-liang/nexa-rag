package com.nexarag.workflow.stream;

import com.nexarag.model.gateway.chat.ChatModelStreamResponse;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Chat 生成结果累积器，线程安全地保存正文、Token 用量和结束原因。
 */
public class ChatGenerationAccumulator {

    private final StringBuilder content = new StringBuilder();
    private Integer promptTokens;
    private Integer completionTokens;
    private Integer totalTokens;
    private String finishReason;
    private volatile String referencesJson;
    private final Map<String, ChatToolOperationDTO> operations = new ConcurrentHashMap<>();

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

    /**
     * 覆盖保存同一工具调用的最新展示状态。
     *
     * @param operation 工具调用展示快照
     */
    public void upsertOperation(ChatToolOperationDTO operation) {
        operations.put(operation.opId(), operation);
    }

    /**
     * 返回按执行顺序排列的工具调用展示快照。
     *
     * @return 工具调用展示快照
     */
    public List<ChatToolOperationDTO> operationsSnapshot() {
        return operations.values().stream()
                .sorted(Comparator.comparingLong(ChatToolOperationDTO::sequence))
                .toList();
    }

    /**
     * 保存已经生成并持久化的引用清单，供异常或取消时的终态写入复用。
     *
     * @param referencesJson 引用清单 JSON
     */
    public void recordReferencesJson(String referencesJson) {
        this.referencesJson = referencesJson;
    }

    /**
     * 获取已生成的引用清单 JSON。
     *
     * @return 引用清单 JSON；尚未生成时为 {@code null}
     */
    public String referencesJson() {
        return referencesJson;
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
