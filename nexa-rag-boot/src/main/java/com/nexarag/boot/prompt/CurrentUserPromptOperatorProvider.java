package com.nexarag.boot.prompt;

import com.nexarag.auth.context.UserContext;
import com.nexarag.model.prompt.PromptOperatorProvider;
import org.springframework.stereotype.Component;

/**
 * 基于认证请求上下文提供 Prompt 操作人的适配器。
 */
@Component
public class CurrentUserPromptOperatorProvider implements PromptOperatorProvider {

    /**
     * 获取当前认证用户作为 Prompt 操作人。
     *
     * @return 当前用户 ID
     */
    @Override
    public String getCurrentOperator() {
        // 1. 从认证模块维护的请求线程上下文读取当前用户
        return UserContext.getCurrUser().userId();
    }
}
