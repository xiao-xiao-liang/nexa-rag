package com.nexarag.infra.alert;

import com.nexarag.common.exception.ServiceException;
import com.nexarag.infra.alert.model.AlertChannel;
import com.nexarag.infra.alert.model.AlertMessage;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * 告警测试服务测试，验证测试消息仅按指定渠道分发。
 */
class AlertTestServiceTest {

    private final AlertDispatcher dispatcher = mock(AlertDispatcher.class);
    private final AlertTestService service = new AlertTestService(dispatcher);

    @Test
    void shouldDispatchTestAlertToRequestedChannel() {
        service.send("email");

        ArgumentCaptor<AlertMessage> captor = ArgumentCaptor.forClass(AlertMessage.class);
        verify(dispatcher).dispatch(captor.capture());
        AlertMessage message = captor.getValue();
        assertThat(message.channel()).isEqualTo(AlertChannel.EMAIL);
        assertThat(message.failureReason()).contains("测试告警");
    }

    @Test
    void shouldRejectUnknownChannel() {
        assertThatThrownBy(() -> service.send("unknown"))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("不支持的告警渠道");
    }
}
