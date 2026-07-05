package com.nexarag.model.refresh;

import com.nexarag.common.error.BaseErrorCode;
import com.nexarag.common.exception.ServiceException;
import com.nexarag.model.config.ModelRegistryRefreshProperties;
import com.nexarag.model.enums.ModelRefreshChannel;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 默认模型注册表变更消息发布器，根据配置选择 MQ 或 PubSub 通道。
 */
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
        ModelRefreshMessageClient client = messageClients.stream()
                .filter(messageClient -> messageClient.channel() == channel)
                .findFirst()
                .orElseThrow(() -> new ServiceException("未找到模型注册表刷新消息客户端，channel=" + channel,
                        BaseErrorCode.SERVICE_ERROR));

        // 2. 发布模型注册表刷新消息
        client.publish(properties.getRefreshTopic(), message);
    }
}
