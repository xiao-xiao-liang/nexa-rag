package com.nexarag.model.prompt.domain;

import com.nexarag.common.exception.ClientException;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 单次执行绑定的 Prompt 版本快照，保证执行期间不受后续发布影响。
 */
public final class PromptExecutionSnapshot {

    private final Map<String, PromptSnapshot> prompts;

    private PromptExecutionSnapshot(Map<String, PromptSnapshot> prompts) {
        this.prompts = Map.copyOf(new LinkedHashMap<>(prompts));
    }

    /**
     * 创建 Prompt 执行快照。
     *
     * @param prompts 按 Prompt 编码索引的版本快照
     * @return 执行快照
     */
    public static PromptExecutionSnapshot of(Map<String, PromptSnapshot> prompts) {
        return new PromptExecutionSnapshot(prompts);
    }

    /**
     * 获取指定 Prompt 的已绑定版本。
     *
     * @param promptCode Prompt 编码
     * @return 已绑定的版本快照
     */
    public PromptSnapshot get(String promptCode) {
        PromptSnapshot snapshot = prompts.get(promptCode);
        if (snapshot == null) {
            throw new ClientException("Prompt执行快照不存在，promptCode=" + promptCode);
        }
        return snapshot;
    }

    /**
     * 单个 Prompt 的不可变执行版本信息。
     *
     * @param promptCode      Prompt 编码
     * @param versionId       版本ID
     * @param releaseId       发布记录ID
     * @param releaseRevision 发布代次
     * @param content         模板正文
     * @param variableSchema  版本变量契约快照
     */
    public record PromptSnapshot(String promptCode, Long versionId, Long releaseId, Long releaseRevision,
                                 String content, PromptVariableSchema variableSchema) {
    }
}
