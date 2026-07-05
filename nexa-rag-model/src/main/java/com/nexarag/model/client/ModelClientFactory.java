package com.nexarag.model.client;

import org.springframework.stereotype.Component;

/**
 * 模型客户端工厂，负责管理动态模型客户端缓存。
 */
@Component
public class ModelClientFactory {

    /**
     * 清理模型客户端缓存。
     */
    public void clear() {
        // 1. 初版尚未创建真实客户端缓存，后续 Provider 接入时扩展此方法。
    }
}
