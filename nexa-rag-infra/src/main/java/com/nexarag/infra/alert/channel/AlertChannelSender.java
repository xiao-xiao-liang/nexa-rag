package com.nexarag.infra.alert.channel;

import com.nexarag.infra.alert.model.AlertChannel;
import com.nexarag.infra.alert.model.AlertMessage;

/**
 * 单个告警外部渠道的发送契约。
 */
public interface AlertChannelSender {

    /**
     * 返回该发送器处理的渠道。
     *
     * @return 告警渠道
     */
    AlertChannel channel();

    /**
     * 发送告警消息。
     *
     * @param message 已脱敏的告警消息
     */
    void send(AlertMessage message);
}
