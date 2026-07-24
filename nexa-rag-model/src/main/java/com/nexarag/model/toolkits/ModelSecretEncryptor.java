package com.nexarag.model.toolkits;

import com.nexarag.common.error.BaseErrorCode;
import com.nexarag.common.exception.ServiceException;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * 模型密钥加密器，负责 API Key 的 AES-GCM 加密、解密和脱敏。
 */
public class ModelSecretEncryptor {

    private static final int IV_LENGTH = 12;
    private static final int TAG_LENGTH = 128;
    private static final int AES_128_KEY_LENGTH = 16;
    private static final int AES_192_KEY_LENGTH = 24;
    private static final int AES_256_KEY_LENGTH = 32;

    private final SecretKeySpec secretKeySpec;
    private final SecureRandom secureRandom = new SecureRandom();

    /**
     * 创建模型密钥加密器。
     *
     * @param masterKey AES 主密钥，支持 Base64 编码密钥或 16、24、32 字节原始字符串密钥
     * @throws IllegalArgumentException 主密钥为空或长度非法时抛出
     */
    public ModelSecretEncryptor(String masterKey) {
        if (masterKey == null || masterKey.isBlank()) {
            throw new IllegalArgumentException("模型密钥主密钥不能为空");
        }
        byte[] keyBytes = resolveKeyBytes(masterKey);
        if (!isValidAesKeyLength(keyBytes.length)) {
            throw new IllegalArgumentException("模型密钥主密钥长度必须为16、24或32字节");
        }
        this.secretKeySpec = new SecretKeySpec(keyBytes, "AES");
    }

    /**
     * 加密 API Key。
     *
     * @param rawSecret API Key 明文
     * @return 加密后的密文，空明文返回 null
     */
    public String encrypt(String rawSecret) {
        if (rawSecret == null || rawSecret.isBlank()) {
            return null;
        }
        try {
            // 1. 生成每次加密独立使用的 IV
            byte[] iv = new byte[IV_LENGTH];
            secureRandom.nextBytes(iv);

            // 2. 使用 AES-GCM 加密明文
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, secretKeySpec, new GCMParameterSpec(TAG_LENGTH, iv));
            byte[] encrypted = cipher.doFinal(rawSecret.getBytes(StandardCharsets.UTF_8));

            // 3. 拼接 IV 和密文并进行 Base64 编码
            byte[] payload = new byte[iv.length + encrypted.length];
            System.arraycopy(iv, 0, payload, 0, iv.length);
            System.arraycopy(encrypted, 0, payload, iv.length, encrypted.length);
            return Base64.getEncoder().encodeToString(payload);
        } catch (Exception exception) {
            throw new ServiceException("模型密钥加密失败", exception, BaseErrorCode.SERVICE_ERROR);
        }
    }

    /**
     * 解密 API Key。
     *
     * @param cipherSecret API Key 密文
     * @return API Key 明文，空密文返回 null
     */
    public String decrypt(String cipherSecret) {
        if (cipherSecret == null || cipherSecret.isBlank()) {
            return null;
        }
        try {
            // 1. 解码密文并拆分 IV
            byte[] payload = Base64.getDecoder().decode(cipherSecret);
            byte[] iv = new byte[IV_LENGTH];
            byte[] encrypted = new byte[payload.length - IV_LENGTH];
            System.arraycopy(payload, 0, iv, 0, IV_LENGTH);
            System.arraycopy(payload, IV_LENGTH, encrypted, 0, encrypted.length);

            // 2. 使用 AES-GCM 解密明文
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, secretKeySpec, new GCMParameterSpec(TAG_LENGTH, iv));
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (Exception exception) {
            throw new ServiceException("模型密钥解密失败", exception, BaseErrorCode.SERVICE_ERROR);
        }
    }

    /**
     * 对 API Key 进行脱敏展示。
     *
     * @param rawSecret API Key 明文
     * @return 脱敏后的 API Key，空明文返回 null
     */
    public String mask(String rawSecret) {
        if (rawSecret == null || rawSecret.isBlank()) {
            return null;
        }
        if (rawSecret.length() <= 4) {
            return "****";
        }
        String prefix = rawSecret.startsWith("sk-") ? "sk-" : "";
        String suffix = rawSecret.substring(rawSecret.length() - 4);
        return prefix + "****" + suffix;
    }

    private boolean isValidAesKeyLength(int keyLength) {
        return keyLength == AES_128_KEY_LENGTH
                || keyLength == AES_192_KEY_LENGTH
                || keyLength == AES_256_KEY_LENGTH;
    }

    private byte[] resolveKeyBytes(String masterKey) {
        String normalizedMasterKey = masterKey.trim();

        // 1. 优先兼容配置文件中更适合保存的 Base64 编码密钥
        try {
            byte[] decodedKeyBytes = Base64.getDecoder().decode(normalizedMasterKey);
            if (isValidAesKeyLength(decodedKeyBytes.length)) {
                return decodedKeyBytes;
            }
        } catch (IllegalArgumentException ignored) {
            // 2. 非 Base64 格式时回退到原始字符串密钥，兼容旧配置
        }

        // 3. 使用原始字符串字节作为密钥
        return normalizedMasterKey.getBytes(StandardCharsets.UTF_8);
    }
}
