package com.nexarag.auth.service.impl;

import com.nexarag.auth.constants.PasswordHashConstants;
import com.nexarag.auth.service.PasswordPolicy;
import com.nexarag.auth.service.PasswordService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * 基于 Argon2id 的本地密码哈希和验证实现。
 */
@Service
@RequiredArgsConstructor
public class PasswordServiceImpl implements PasswordService {

    private final PasswordPolicy passwordPolicy;

    private final Argon2PasswordEncoder passwordEncoder = new Argon2PasswordEncoder(
            PasswordHashConstants.SALT_LENGTH,
            PasswordHashConstants.HASH_LENGTH,
            PasswordHashConstants.PARALLELISM,
            PasswordHashConstants.MEMORY_KIB,
            PasswordHashConstants.ITERATIONS);

    /**
     * {@inheritDoc}
     */
    @Override
    public String hash(String rawPassword) {
        // 1. 统一执行本地密码规则校验
        passwordPolicy.validate(rawPassword);

        // 2. 使用随机盐生成可升级的 Argon2id PHC 哈希
        return passwordEncoder.encode(rawPassword);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String rehashVerified(String rawPassword) {
        return passwordEncoder.encode(rawPassword);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean matches(String rawPassword, String passwordHash) {
        // 1. 空值和损坏哈希一律视为不匹配，避免登录入口暴露内部状态
        if (rawPassword == null || passwordHash == null || passwordHash.isBlank()) {
            return false;
        }

        // 2. Spring Security 使用常量时间比较验证 Argon2id 哈希
        try {
            return passwordEncoder.matches(rawPassword, passwordHash);
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean shouldUpgrade(String passwordHash) {
        if (passwordHash == null || passwordHash.isBlank()) {
            return false;
        }
        try {
            return passwordEncoder.upgradeEncoding(passwordHash);
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }
}
