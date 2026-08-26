package com.nexarag.auth.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * 邮箱验证码哈希器，使用部署侧 Pepper 避免验证码泄露后的离线枚举。
 */
@Component
public class EmailVerificationCodeHasher {

    /** HMAC SHA-256 算法名称。 */
    private static final String HMAC_SHA_256 = "HmacSHA256";

    private final SecretKeySpec pepperKey;

    /**
     * 使用部署环境中单独配置的验证码 Pepper 创建哈希器。
     *
     * @param pepper 服务端验证码 Pepper
     */
    public EmailVerificationCodeHasher(@Value("${nexa.auth.email-code-pepper:}") String pepper) {
        if (pepper == null || pepper.isBlank()) {
            throw new IllegalArgumentException("认证邮箱验证码 Pepper 未配置");
        }
        this.pepperKey = new SecretKeySpec(pepper.getBytes(StandardCharsets.UTF_8), HMAC_SHA_256);
    }

    /**
     * 计算绑定验证码上下文的 HMAC 哈希。
     *
     * @param contextHash 挑战上下文哈希
     * @param verificationCode 用户验证码
     * @return 十六进制 HMAC 哈希
     */
    public String hash(String contextHash, String verificationCode) {
        try {
            Mac mac = Mac.getInstance(HMAC_SHA_256);
            mac.init(pepperKey);
            String value = (contextHash == null ? "" : contextHash) + '|'
                    + (verificationCode == null ? "" : verificationCode);
            return HexFormat.of().formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("JVM 未提供 HMAC-SHA-256 算法", exception);
        } catch (java.security.InvalidKeyException exception) {
            throw new IllegalStateException("认证邮箱验证码 Pepper 不可用", exception);
        }
    }

    /**
     * 常量时间校验验证码哈希。
     *
     * @param contextHash 挑战上下文哈希
     * @param verificationCode 用户验证码
     * @param expectedHash Redis 中保存的预期哈希
     * @return 验证码匹配时返回 true
     */
    public boolean matches(String contextHash, String verificationCode, String expectedHash) {
        return expectedHash != null && MessageDigest.isEqual(hash(contextHash, verificationCode)
                .getBytes(StandardCharsets.UTF_8), expectedHash.getBytes(StandardCharsets.UTF_8));
    }
}
