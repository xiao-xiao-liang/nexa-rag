package com.nexarag.model.service;

import com.nexarag.model.dto.prompt.PromptResponse;

import java.util.List;
import java.util.Map;

/**
 * Prompt 在线管理查询与脱敏预览服务。
 */
public interface PromptManagementService {

    /**
     * 查询全部 Prompt 定义摘要。
     *
     * @return Prompt 定义列表
     */
    List<PromptResponse> listPrompts();

    /**
     * 查询指定 Prompt 及其版本、发布历史。
     *
     * @param promptCode Prompt 编码
     * @return Prompt 管理详情
     */
    PromptResponse getPrompt(String promptCode);

    /**
     * 使用脱敏示例变量渲染待提交正文，不写入数据库也不调用模型。
     *
     * @param promptCode Prompt 编码
     * @param content 待预览模板正文
     * @param variables 预留请求变量，实际不会参与渲染
     * @return 脱敏渲染后的正文
     */
    String preview(String promptCode, String content, Map<String, Object> variables);
}
