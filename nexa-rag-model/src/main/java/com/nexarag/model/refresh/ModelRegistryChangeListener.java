package com.nexarag.model.refresh;

/**
 * 模型注册表变更消息监听器。
 */
public interface ModelRegistryChangeListener {

    /**
     * 处理模型注册表变更消息。
     *
     * @param message 注册表变更消息
     */
    void onMessage(ModelRegistryChangedMessage message);
}
