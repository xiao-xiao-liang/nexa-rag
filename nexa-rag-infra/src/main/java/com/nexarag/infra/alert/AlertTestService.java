package com.nexarag.infra.alert;

import com.nexarag.common.exception.ServiceException;
import com.nexarag.infra.alert.model.AlertChannel;
import com.nexarag.infra.alert.model.AlertMessage;
import com.nexarag.infra.alert.model.AlertSeverity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Locale;

/**
 * 告警渠道连通性测试服务，通过既有分发链路发送不关联真实任务的测试告警。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AlertTestService {

    private static final long TEST_OUTBOX_ID = 1L;
    private static final long TEST_DOCUMENT_ID = 1L;
    private static final long TEST_PARENT_OUTBOX_ID = 1L;
    private static final String TEST_OPERATION_ID = "alert-channel-connectivity-test";
    private static final String TEST_TASK_TYPE = "ALERT_CHANNEL_CONNECTIVITY_TEST";
    private static final String TEST_FAILURE_REASON = "这是一条 NexaRAG 测试告警，用于验证告警渠道连通性，不对应真实任务失败。";

    private final AlertDispatcher alertDispatcher;

    /**
     * 向指定渠道发送测试告警。
     *
     * @param channelText 告警渠道名称
     */
    public void send(String channelText) {
        // 1. 解析并校验渠道名称，避免错误渠道进入真实发送链路
        AlertChannel channel = parseChannel(channelText);

        // 2. 构造不关联真实 Outbox 的固定测试消息，并委托既有分发器投递
        log.info("发送告警渠道连通性测试，channel={}", channel);
        alertDispatcher.dispatch(buildTestMessage(channel));
    }

    private AlertChannel parseChannel(String channelText) {
        if (channelText == null || channelText.isBlank()) {
            throw new ServiceException("告警渠道不能为空");
        }
        try {
            return AlertChannel.valueOf(channelText.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new ServiceException("不支持的告警渠道，channel=" + channelText);
        }
    }

    private AlertMessage buildTestMessage(AlertChannel channel) {
        return new AlertMessage(TEST_OUTBOX_ID, TEST_DOCUMENT_ID, TEST_PARENT_OUTBOX_ID, TEST_OPERATION_ID,
                TEST_TASK_TYPE, AlertSeverity.WARNING, channel, TEST_FAILURE_REASON, 1, LocalDateTime.now());
    }
}
