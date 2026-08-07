package com.nexarag.infra.alert.controller;

import com.nexarag.common.web.Result;
import com.nexarag.infra.alert.AlertTestService;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * 告警测试接口测试，验证渠道参数交由测试服务处理。
 */
class AlertTestControllerTest {

    @Test
    void shouldDelegateRequestedChannel() {
        AlertTestService service = mock(AlertTestService.class);
        AlertTestController controller = new AlertTestController(service);

        Result<Void> result = controller.send("feishu");

        verify(service).send("feishu");
        assertThat(result.code()).isEqualTo(Result.SUCCESS_CODE);
    }
}
