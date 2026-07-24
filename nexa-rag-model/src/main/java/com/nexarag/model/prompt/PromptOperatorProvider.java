package com.nexarag.model.prompt;

/**
 * Prompt 管理操作人的请求上下文适配接口，隔离模型模块与认证模块的依赖。
 */
public interface PromptOperatorProvider {

    /**
     * 获取当前请求的 Prompt 操作人标识。
     *
     * @return 当前操作人标识
     */
    String getCurrentOperator();
}
