package com.nexarag.model.refresh;

import com.nexarag.model.registry.ModelRegistryRefresher;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 默认模型注册表变更消息监听器，收到消息后刷新本地模型注册表。
 */
@Service
@RequiredArgsConstructor
public class DefaultModelRegistryChangeListener implements ModelRegistryChangeListener {

    private final ModelRegistryRefresher modelRegistryRefresher;

    @Override
    public void onMessage(ModelRegistryChangedMessage message) {
        // 1. 根据消息版本刷新本地模型注册表
        modelRegistryRefresher.refreshIfNewer(message.versionNo());
    }
}
