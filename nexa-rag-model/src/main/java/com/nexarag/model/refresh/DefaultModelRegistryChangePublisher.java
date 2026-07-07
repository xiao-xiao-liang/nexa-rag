package com.nexarag.model.refresh;

import com.nexarag.model.config.ModelRegistryRefreshProperties;
import com.nexarag.model.enums.ModelRefreshChannel;
import com.nexarag.model.registry.ModelRegistryRefresher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 默认模型注册表变更消息发布器，根据配置选择本地、Redis Pub/Sub 或预留 MQ 通道。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DefaultModelRegistryChangePublisher implements ModelRegistryChangePublisher {

    private final ModelRegistryRefreshProperties properties;
    private final List<ModelRefreshMessageClient> messageClients;
    private final ModelRegistryRefresher modelRegistryRefresher;

    @Override
    public void publish(long versionNo) {
        ModelRefreshChannel channel = properties.getRefreshChannel();
        if (channel == ModelRefreshChannel.LOCAL) {
            // 1. 本地模式直接刷新当前 JVM 快照
            modelRegistryRefresher.refreshIfNewer(versionNo);
            return;
        }
        if (channel == ModelRefreshChannel.INFRA_MQ) {
            // 2. INFRA_MQ 当前阶段仅预留，避免误以为已经跨实例通知
            log.warn("模型注册表刷新通道暂未接入 INFRA_MQ，versionNo={}", versionNo);
            return;
        }

        // 3. Redis Pub/Sub 模式发布轻量刷新消息
        ModelRefreshMessageClient client = messageClients == null ? null : messageClients.stream()
                .filter(messageClient -> messageClient.channel() == channel)
                .findFirst()
                .orElse(null);
        if (client == null) {
            log.warn("未找到模型注册表刷新消息客户端，本次仅更新版本号，channel={}，versionNo={}", channel, versionNo);
            return;
        }

        try {
            // 4. 发布模型注册表刷新消息
            client.publish(properties.getRefreshTopic(), new ModelRegistryChangedMessage(versionNo, channel));
        } catch (Exception exception) {
            log.warn("发布模型注册表刷新消息失败，本次仅更新版本号，channel={}，topic={}，versionNo={}",
                    channel, properties.getRefreshTopic(), versionNo, exception);
        }
    }
}
