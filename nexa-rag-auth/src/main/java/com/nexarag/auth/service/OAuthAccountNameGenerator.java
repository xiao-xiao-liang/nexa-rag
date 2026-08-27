package com.nexarag.auth.service;

import com.nexarag.auth.enums.OAuthProvider;
import com.nexarag.auth.enums.AuthErrorCode;
import com.nexarag.auth.mapper.AuthUserMapper;
import com.nexarag.common.error.BaseErrorCode;
import com.nexarag.common.exception.ClientException;
import com.nexarag.common.exception.ServiceException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * 为 OAuth 首次登录生成合法且可用的本地账号名。
 *
 * <p>第三方主体仅参与不可逆哈希计算，绝不直接写入账号名或日志；第三方展示名称单独持久化。</p>
 */
@Component
@RequiredArgsConstructor
public class OAuthAccountNameGenerator {

    /** 哈希算法名称。 */
    private static final String HASH_ALGORITHM = "SHA-256";

    /** 用作账号名的哈希十六进制字符数量。 */
    private static final int HASH_HEX_LENGTH = 16;

    /** 生成账号名的最大探测次数。 */
    private static final int MAX_CANDIDATE_ATTEMPTS = 100;

    private final AccountNamePolicy accountNamePolicy;
    private final AuthUserMapper authUserMapper;

    /**
     * 按显式账号名、合法展示名称和确定性哈希账号名的优先级生成本地账号名。
     *
     * @param provider OAuth 提供方
     * @param providerSubject 提供方稳定主体标识
     * @param displayName 第三方原始展示名称，可为空
     * @param explicitAccountName 调用方显式指定的账号名，可为空
     * @return 可用于创建用户的合法账号名
     */
    public String generate(OAuthProvider provider, String providerSubject, String displayName,
                           String explicitAccountName) {
        // 1. 保持既有接口兼容：显式账号名仍由原有创建服务校验和判重。
        if (hasText(explicitAccountName)) {
            return explicitAccountName;
        }

        // 2. 优先采用满足本地账号规则且尚未被占用的第三方展示名称。
        String availableDisplayName = findAvailableDisplayName(displayName);
        if (availableDisplayName != null) {
            return availableDisplayName;
        }

        // 3. 展示名称不可用时，以提供方与稳定主体计算不可逆哈希，避免泄露第三方标识。
        String hashPrefix = calculateHashPrefix(provider, providerSubject);
        for (int attempt = 1; attempt <= MAX_CANDIDATE_ATTEMPTS; attempt++) {
            String candidate = provider.getCode() + '-' + hashPrefix;
            if (attempt > 1) {
                candidate += '-' + String.valueOf(attempt);
            }
            if (authUserMapper.selectByAccountNameKey(candidate) == null) {
                return candidate;
            }
        }
        throw new ServiceException("OAuth 自动生成账号名失败，请稍后重试");
    }

    /**
     * 尝试将第三方展示名称作为本地账号名；格式不合法或已占用时返回空。
     */
    private String findAvailableDisplayName(String displayName) {
        if (!hasText(displayName)) {
            return null;
        }
        String accountName = displayName.trim();
        try {
            String accountNameKey = accountNamePolicy.normalizeAndValidate(accountName);
            return authUserMapper.selectByAccountNameKey(accountNameKey) == null ? accountName : null;
        } catch (ClientException exception) {
            if (AuthErrorCode.ACCOUNT_NAME_INVALID.code().equals(exception.getErrorCode())) {
                return null;
            }
            throw exception;
        }
    }

    /**
     * 计算不可逆哈希前缀，原始稳定主体不写入可见账号名。
     */
    private String calculateHashPrefix(OAuthProvider provider, String providerSubject) {
        try {
            MessageDigest messageDigest = MessageDigest.getInstance(HASH_ALGORITHM);
            byte[] digest = messageDigest.digest((provider.getCode() + ':' + providerSubject)
                    .getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest).substring(0, HASH_HEX_LENGTH);
        } catch (NoSuchAlgorithmException exception) {
            throw new ServiceException("OAuth 自动生成账号名失败", exception, BaseErrorCode.SERVICE_ERROR);
        }
    }

    /**
     * 判断字符串是否包含非空白内容。
     */
    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
