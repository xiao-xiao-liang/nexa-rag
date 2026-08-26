package com.nexarag.auth.mail;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

/**
 * 认证邮件事务消息验证码载荷加密器，避免明文验证码进入消息中间件。
 */
public class AuthMailMessageCipher {

    /** AES-GCM 认证标签长度，单位：位。 */
    private static final int GCM_TAG_LENGTH_BITS = 128;

    /** AES-GCM 推荐随机初始化向量长度，单位：字节。 */
    private static final int GCM_IV_LENGTH_BYTES = 12;

    /** AES 合法密钥长度。 */
    private static final int AES_128_KEY_LENGTH = 16;
    private static final int AES_192_KEY_LENGTH = 24;
    private static final int AES_256_KEY_LENGTH = 32;

    private final SecretKey secretKey;
    private final SecureRandom secureRandom = new SecureRandom();

    /**
     * 根据 Base64 编码的 AES 主密钥创建加密器。
     *
     * @param base64MasterKey Base64 编码的 16、24 或 32 字节 AES 密钥
     */
    public AuthMailMessageCipher(String base64MasterKey) {
        byte[] keyBytes;
        try {
            keyBytes = Base64.getDecoder().decode(base64MasterKey);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("认证邮件事务消息密钥必须为有效 Base64", exception);
        }
        if (!isSupportedAesKeyLength(keyBytes.length)) {
            throw new IllegalArgumentException("认证邮件事务消息密钥长度必须为 16、24 或 32 字节");
        }
        this.secretKey = new SecretKeySpec(keyBytes, "AES");
    }

    /**
     * 加密验证码，并将随机 IV 与密文拼接为 Base64 文本。
     *
     * @param verificationCode 明文验证码
     * @return 可安全放入消息体的密文
     */
    public String encrypt(String verificationCode) {
        if (verificationCode == null || verificationCode.isBlank()) {
            throw new IllegalArgumentException("验证码不能为空");
        }
        byte[] iv = new byte[GCM_IV_LENGTH_BYTES];
        secureRandom.nextBytes(iv);
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));
            byte[] encrypted = cipher.doFinal(verificationCode.getBytes(StandardCharsets.UTF_8));
            byte[] payload = Arrays.copyOf(iv, iv.length + encrypted.length);
            System.arraycopy(encrypted, 0, payload, iv.length, encrypted.length);
            return Base64.getEncoder().encodeToString(payload);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("认证邮件事务消息加密失败", exception);
        }
    }

    /**
     * 解密来自事务消息的验证码载荷。
     *
     * @param ciphertext Base64 编码的 IV 与密文拼接载荷
     * @return 明文验证码
     */
    public String decrypt(String ciphertext) {
        try {
            byte[] payload = Base64.getDecoder().decode(ciphertext);
            if (payload.length <= GCM_IV_LENGTH_BYTES) {
                throw new IllegalArgumentException("认证邮件事务消息密文格式不合法");
            }
            byte[] iv = Arrays.copyOf(payload, GCM_IV_LENGTH_BYTES);
            byte[] encrypted = Arrays.copyOfRange(payload, GCM_IV_LENGTH_BYTES, payload.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, secretKey, new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (GeneralSecurityException exception) {
            throw new IllegalArgumentException("认证邮件事务消息解密失败", exception);
        }
    }

    private boolean isSupportedAesKeyLength(int keyLength) {
        return keyLength == AES_128_KEY_LENGTH
                || keyLength == AES_192_KEY_LENGTH
                || keyLength == AES_256_KEY_LENGTH;
    }
}
