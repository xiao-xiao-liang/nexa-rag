package com.nexarag.auth.context;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 验证当前用户上下文的线程隔离行为。
 */
class CurrentUserContextTest {

    @AfterEach
    void clearContext() {
        CurrentUserContext.clear();
    }

    @Test
    void shouldRejectAccessWhenRequestUserIsMissing() {
        assertThatThrownBy(CurrentUserContext::getRequired)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("当前请求未设置用户身份");
    }
}
