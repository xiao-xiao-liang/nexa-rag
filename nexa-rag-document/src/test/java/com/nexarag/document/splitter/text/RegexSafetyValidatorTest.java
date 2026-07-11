package com.nexarag.document.splitter.text;

import com.nexarag.common.exception.ServiceException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 正则安全校验器测试。
 */
class RegexSafetyValidatorTest {

    private final RegexSafetyValidator validator = new RegexSafetyValidator();

    @Test
    void validateShouldAcceptSimpleRegex() {
        assertThatCode(() -> validator.validate("\\r?\\n"))
                .doesNotThrowAnyException();
    }

    @Test
    void validateShouldRejectInvalidRegex() {
        assertThatThrownBy(() -> validator.validate("["))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("正则表达式语法不合法");
    }

    @Test
    void validateShouldRejectNestedQuantifier() {
        assertThatThrownBy(() -> validator.validate("(a+)+"))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("嵌套量词");
    }

    @Test
    void validateShouldRejectTooLongRegex() {
        assertThatThrownBy(() -> validator.validate("a".repeat(257)))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("不能超过256个字符");
    }
}
