package com.nexarag.model.refresh;

import com.nexarag.model.config.ModelRegistryRefreshProperties;
import com.nexarag.model.enums.ModelRefreshChannel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 默认模型注册表变更消息发布器，根据配置选择 MQ 或 PubSub 通道。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DefaultModelRegistryChangePublisher implements ModelRegistryChangePublisher {

    private final ModelRegistryRefreshProperties properties;
    private final List<ModelRefreshMessageClient> messageClients;

    @Override
    public void publish(long versionNo) {
        ModelRefreshChannel channel = properties.getRefreshChannel();
        ModelRegistryChangedMessage message = new ModelRegistryChangedMessage(versionNo, channel);

        // 1. 根据配置通道查找消息客户端
        ModelRefreshMessageClient client = messageClients == null ? null : messageClients.stream()
                .filter(messageClient -> messageClient.channel() == channel)
                .findFirst()
                .orElse(null);
        if (client == null) {
            log.warn("未找到模型注册表刷新消息客户端，本次仅更新版本号，channel={}，versionNo={}", channel, versionNo);
            return;
        }

        try {
            // 2. 发布模型注册表刷新消息
            client.publish(properties.getRefreshTopic(), message);
        } catch (Exception exception) {
            log.warn("发布模型注册表刷新消息失败，本次仅更新版本号，channel={}，topic={}，versionNo={}",
                    channel, properties.getRefreshTopic(), versionNo, exception);
        }
    }
}
