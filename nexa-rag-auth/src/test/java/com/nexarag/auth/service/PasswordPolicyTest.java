package com.nexarag.auth.service;

import com.nexarag.common.exception.ClientException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 本地密码规则测试。
 */
class PasswordPolicyTest {

    private final PasswordPolicy passwordPolicy = new PasswordPolicy();

    /**
     * 验证 15 位任意字符的密码可被接受。
     */
    @Test
    void shouldAcceptAtLeastFifteenCharacters() {
        assertThatCode(() -> passwordPolicy.validate("一二三四五六七八九十甲乙丙丁戊"))
                .doesNotThrowAnyException();
    }

    /**
     * 验证八位密码需要同时包含小写 ASCII 字母和数字。
     */
    @Test
    void shouldAcceptEightCharactersWithLowercaseAsciiLetterAndDigit() {
        assertThatCode(() -> passwordPolicy.validate("nexa1234"))
                .doesNotThrowAnyException();
    }

    /**
     * 验证既不满足长密码也不满足字符组合的密码会被拒绝。
     */
    @Test
    void shouldRejectPasswordThatDoesNotMeetGithubRule() {
        assertThatThrownBy(() -> passwordPolicy.validate("ABCDEFGH"))
                .isInstanceOf(ClientException.class)
                .extracting(throwable -> ((ClientException) throwable).getErrorMessage())
                .isEqualTo("密码不符合安全要求");
    }
}
