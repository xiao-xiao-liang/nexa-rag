package com.nexarag.model.refresh;

/**
 * 模型注册表变更消息发布器。
 */
public interface ModelRegistryChangePublisher {

    /**
     * 发布模型注册表变更消息。
     *
     * @param versionNo 全局模型注册表版本号
     */
    void publish(long versionNo);
}
