package com.nexarag.common.web;

import com.nexarag.common.error.BaseErrorCode;
import com.nexarag.common.exception.ServiceException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 统一响应构造工具测试。
 */
class ResultsTest {

    @Test
    void resultShouldSupportBuilder() {
        Result<String> result = Result.<String>builder()
                .code(Result.SUCCESS_CODE)
                .message("操作成功")
                .data("ok")
                .traceId("trace-001")
                .build();

        assertThat(result.code()).isEqualTo(Result.SUCCESS_CODE);
        assertThat(result.message()).isEqualTo("操作成功");
        assertThat(result.data()).isEqualTo("ok");
        assertThat(result.traceId()).isEqualTo("trace-001");
    }

    @Test
    void successShouldUseSuccessCode() {
        Result<String> result = Results.success("ok");

        assertThat(result.code()).isEqualTo(Result.SUCCESS_CODE);
        assertThat(result.data()).isEqualTo("ok");
    }

    @Test
    void failureShouldUseBaseErrorCode() {
        Result<Void> result = Results.failure(BaseErrorCode.CLIENT_ERROR.code(), "参数不合法");

        assertThat(result.code()).isEqualTo("A000001");
        assertThat(result.message()).isEqualTo("参数不合法");
        assertThat(result.data()).isNull();
    }

    @Test
    void failureShouldUseAbstractException() {
        Result<Void> result = Results.failure(new ServiceException("模型调用失败"));

        assertThat(result.code()).isEqualTo(BaseErrorCode.SERVICE_ERROR.code());
        assertThat(result.message()).isEqualTo("模型调用失败");
        assertThat(result.data()).isNull();
    }
}
