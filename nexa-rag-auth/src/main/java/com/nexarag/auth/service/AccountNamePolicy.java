package com.nexarag.auth.service;

import com.nexarag.auth.enums.AuthErrorCode;
import com.nexarag.common.exception.ClientException;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * GitHub 风格账号名规则校验和规范化服务。
 */
@Component
public class AccountNamePolicy {

    private static final Pattern ACCOUNT_NAME_PATTERN = Pattern.compile("^[A-Za-z0-9](?:[A-Za-z0-9-]{0,37}[A-Za-z0-9])?$");

    /**
     * 校验并返回用于唯一匹配的账号名键。
     *
     * @param accountName 用户输入账号名
     * @return 小写规范化账号名键
     */
    public String normalizeAndValidate(String accountName) {
        if (accountName == null) {
            throw new ClientException(AuthErrorCode.ACCOUNT_NAME_INVALID);
        }
        String normalizedAccountName = accountName.trim();
        if (!ACCOUNT_NAME_PATTERN.matcher(normalizedAccountName).matches()) {
            throw new ClientException(AuthErrorCode.ACCOUNT_NAME_INVALID);
        }
        return normalizedAccountName.toLowerCase(Locale.ROOT);
    }
}
