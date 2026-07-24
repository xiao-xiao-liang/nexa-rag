package com.nexarag.model.toolkits.prompt;

import com.nexarag.common.exception.ClientException;
import com.nexarag.model.prompt.domain.PromptExecutionSnapshot;
import com.nexarag.model.prompt.domain.PromptVariableSchema;
import com.samskivert.mustache.Mustache;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Prompt 渲染服务，基于请求级快照输出未进行 HTML 转义的正文。
 */
@Service
public class PromptRender {

    /**
     * 渲染指定快照中的 Prompt 版本。
     *
     * @param snapshot   请求级 Prompt 快照
     * @param promptCode Prompt 编码
     * @param variables  渲染变量
     * @return 带版本追溯信息的渲染结果
     */
    public RenderedPrompt render(PromptExecutionSnapshot snapshot, String promptCode, Map<String, Object> variables) {
        // 1. 从请求级快照读取已绑定版本，避免读取当前发布状态
        PromptExecutionSnapshot.PromptSnapshot prompt = snapshot.get(promptCode);
        validateRequiredVariables(prompt.variableSchema(), variables);

        // 2. 使用关闭 HTML 转义的 Mustache 编译器渲染原文 Prompt
        String content = Mustache.compiler().escapeHTML(false).compile(prompt.content()).execute(variables);
        return new RenderedPrompt(prompt.promptCode(), prompt.versionId(), prompt.releaseId(), prompt.releaseRevision(), content);
    }

    private void validateRequiredVariables(PromptVariableSchema schema, Map<String, Object> variables) {
        Map<String, Object> safeVariables = variables == null ? Map.of() : variables;
        for (String variable : schema.required()) {
            if (!safeVariables.containsKey(variable) || safeVariables.get(variable) == null) {
                throw new ClientException("Prompt缺少必填变量，变量名=" + variable);
            }
        }
    }

    /**
     * 已渲染 Prompt 及其版本追溯信息。
     *
     * @param promptCode      Prompt 编码
     * @param versionId       版本ID
     * @param releaseId       发布记录ID
     * @param releaseRevision 发布代次
     * @param content         渲染后的原文
     */
    public record RenderedPrompt(String promptCode, Long versionId, Long releaseId, Long releaseRevision, String content) {
    }
}
