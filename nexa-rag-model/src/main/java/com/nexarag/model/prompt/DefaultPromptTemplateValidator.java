package com.nexarag.model.prompt;

import com.nexarag.common.exception.ClientException;
import com.nexarag.model.prompt.domain.PromptVariableSchema;
import com.samskivert.mustache.Mustache;
import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 默认 Prompt 模板校验器，校验 Mustache 语法和变量白名单。
 */
@Component
public class DefaultPromptTemplateValidator implements PromptTemplateValidator {

    /**
     * 校验 Prompt 模板正文与变量契约。
     *
     * @param promptCode       Prompt 编码
     * @param content          模板正文
     * @param schema     Prompt 变量契约
     */
    @Override
    public void validate(String promptCode, String content, PromptVariableSchema schema) {
        // 1. 校验基础输入，避免空模板进入发布流程
        if (content == null || content.isBlank()) {
            throw new ClientException("Prompt模板内容不能为空，promptCode=" + promptCode);
        }
        if (schema == null) {
            throw new ClientException("Prompt变量契约不能为空，promptCode=" + promptCode);
        }

        // 2. 编译 Mustache 模板，提前发现语法问题
        try {
            Mustache.compiler().compile(content);
        } catch (RuntimeException exception) {
            throw new ClientException("Prompt模板语法不合法，promptCode=" + promptCode);
        }

        // 3. 严格解析标签并校验变量白名单，防止不完整或特殊标签绕过变量契约
        Set<String> referencedVariables = extractReferencedVariables(promptCode, content, schema.allowed());

        // 4. 确认变量契约中的必填变量均被模板引用，避免无意义的运行时入参要求
        for (String requiredVariable : defaultVariables(schema.required())) {
            if (!referencedVariables.contains(requiredVariable)) {
                throw new ClientException("Prompt模板未引用必填变量，promptCode=" + promptCode
                        + "，variable=" + requiredVariable);
            }
        }
    }

    /**
     * 提取并校验模板变量及逻辑片段标签。
     *
     * @param promptCode Prompt 编码
     * @param content 模板正文
     * @param allowedVariables 允许变量
     * @return 已引用变量集合
     */
    private Set<String> extractReferencedVariables(String promptCode, String content, List<String> allowedVariables) {
        Set<String> referencedVariables = new HashSet<>();
        Deque<String> sections = new ArrayDeque<>();
        int startIndex = 0;
        while ((startIndex = content.indexOf("{{", startIndex)) >= 0) {
            boolean tripleTag = content.startsWith("{{{", startIndex);
            String closeTag = tripleTag ? "}}}" : "}}";
            int tagContentStart = startIndex + (tripleTag ? 3 : 2);
            int endIndex = content.indexOf(closeTag, tagContentStart);
            if (endIndex < 0) {
                throw new ClientException("Prompt模板语法不合法，存在未闭合标签，promptCode=" + promptCode);
            }
            String tag = content.substring(tagContentStart, endIndex).trim();
            validateTag(promptCode, tag, tripleTag, defaultVariables(allowedVariables), referencedVariables, sections);
            startIndex = endIndex + closeTag.length();
        }
        if (!sections.isEmpty()) {
            throw new ClientException("Prompt模板语法不合法，存在未闭合逻辑片段，promptCode=" + promptCode);
        }
        return referencedVariables;
    }

    private void validateTag(String promptCode, String tag, boolean tripleTag, List<String> allowedVariables,
                             Set<String> referencedVariables, Deque<String> sections) {
        if (tag.isEmpty()) {
            throw new ClientException("Prompt模板语法不合法，存在空标签，promptCode=" + promptCode);
        }
        char tagPrefix = tag.charAt(0);
        String variableName = (tagPrefix == '#' || tagPrefix == '^' || tagPrefix == '/') ? tag.substring(1).trim() : tag;
        if (!variableName.matches("[a-zA-Z][a-zA-Z0-9_]*")) {
            throw new ClientException("Prompt模板标签不合法，promptCode=" + promptCode + "，tag=" + tag);
        }
        if (!allowedVariables.contains(variableName)) {
            throw new ClientException("Prompt模板引用未登记变量，promptCode=" + promptCode
                    + "，variable=" + variableName);
        }
        if (tripleTag && tagPrefix != variableName.charAt(0)) {
            throw new ClientException("Prompt模板原文插值不能用于逻辑片段，promptCode=" + promptCode);
        }
        if (tagPrefix == '#' || tagPrefix == '^') {
            sections.push(variableName);
        } else if (tagPrefix == '/') {
            if (sections.isEmpty() || !variableName.equals(sections.pop())) {
                throw new ClientException("Prompt模板逻辑片段未正确闭合，promptCode=" + promptCode);
            }
        }
        referencedVariables.add(variableName);
    }

    private List<String> defaultVariables(List<String> variables) {
        return variables == null ? List.of() : variables;
    }
}
