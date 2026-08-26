package com.nexarag.auth.service;

import com.nexarag.auth.enums.AuthErrorCode;
import com.nexarag.common.exception.ClientException;
import org.springframework.stereotype.Component;

/**
 * 本地密码规则校验器，采用已确认的 GitHub 长度与字符组合规则。
 */
@Component
public class PasswordPolicy {

    /**
     * 校验明文密码是否符合规则。
     *
     * @param rawPassword 待校验明文密码
     * @throws ClientException 密码不符合规则时抛出
     */
    public void validate(String rawPassword) {
        // 1. 拒绝空密码
        if (rawPassword == null || rawPassword.isEmpty()) {
            throw new ClientException(AuthErrorCode.PASSWORD_POLICY_INVALID);
        }

        // 2. 至少 15 位时允许任意字符组合
        if (rawPassword.codePointCount(0, rawPassword.length()) >= 15) {
            return;
        }

        // 3. 八位以上时要求同时包含小写 ASCII 字母与数字
        boolean containsLowercaseAsciiLetter = rawPassword.chars()
                .anyMatch(character -> character >= 'a' && character <= 'z');
        boolean containsDigit = rawPassword.chars().anyMatch(Character::isDigit);
        if (rawPassword.codePointCount(0, rawPassword.length()) >= 8
                && containsLowercaseAsciiLetter && containsDigit) {
            return;
        }

        throw new ClientException(AuthErrorCode.PASSWORD_POLICY_INVALID);
    }
}
