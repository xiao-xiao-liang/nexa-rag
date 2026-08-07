package com.nexarag.infra.alert;

import com.nexarag.common.exception.ServiceException;
import com.nexarag.infra.alert.channel.AlertChannelSender;
import com.nexarag.infra.alert.model.AlertChannel;
import com.nexarag.infra.alert.model.AlertMessage;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * 告警分发器，根据消息渠道路由到对应的发送器。
 */
@Component
public class AlertDispatcher {

    private final Map<AlertChannel, AlertChannelSender> senders;

    public AlertDispatcher(List<AlertChannelSender> channelSenders) {
        this.senders = new EnumMap<>(AlertChannel.class);
        for (AlertChannelSender sender : channelSenders) {
            AlertChannelSender previous = senders.put(sender.channel(), sender);
            if (previous != null) {
                throw new ServiceException("告警渠道发送器重复，channel=" + sender.channel());
            }
        }
    }

    /**
     * 将告警消息分发给匹配渠道。
     *
     * @param message 已脱敏的告警消息
     */
    public void dispatch(AlertMessage message) {
        if (message == null) {
            throw new ServiceException("告警消息不能为空");
        }
        AlertChannelSender sender = senders.get(message.channel());
        if (sender == null) {
            throw new ServiceException("未配置告警渠道，channel=" + message.channel());
        }
        sender.send(message);
    }
}
