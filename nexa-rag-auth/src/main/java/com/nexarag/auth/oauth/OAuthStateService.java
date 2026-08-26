package com.nexarag.auth.oauth;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexarag.auth.enums.OAuthProvider;
import com.nexarag.auth.enums.AuthErrorCode;
import com.nexarag.common.exception.ClientException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;

/**
 * 生成并单次消费 Redis 中的 OAuth state，同时保存绑定会话与 PKCE 上下文。
 */
@Service
@RequiredArgsConstructor
public class OAuthStateService {

    /** state 有效期，超过后必须重新发起授权。 */
    private static final Duration STATE_TTL = Duration.ofMinutes(10);

    /** Redis state 键名前缀。 */
    private static final String STATE_KEY_PREFIX = "nexa:auth:oauth:state:";

    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;
    private final SecureRandom secureRandom = new SecureRandom();

    /**
     * 创建服务端 state 上下文并返回不可预测 state。
     *
     * @param context 单次授权上下文
     * @return 传递给第三方平台的 state
     */
    public String create(OAuthStateContext context) {
        String state = randomValue(32);
        try {
            stringRedisTemplate.opsForValue().set(STATE_KEY_PREFIX + state, objectMapper.writeValueAsString(context), STATE_TTL);
            return state;
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("OAuth state 序列化失败", exception);
        }
    }

    /**
     * 原子单次消费 state，并校验其原始提供方。
     *
     * @param state 平台回传 state
     * @param provider 当前回调 URL 所代表的提供方
     * @return 原始授权上下文
     */
    public OAuthStateContext consume(String state, OAuthProvider provider) {
        if (state == null || state.isBlank()) {
            throw new ClientException(AuthErrorCode.OAUTH_STATE_INVALID);
        }
        String content = stringRedisTemplate.opsForValue().getAndDelete(STATE_KEY_PREFIX + state);
        if (content == null) {
            throw new ClientException(AuthErrorCode.OAUTH_STATE_INVALID);
        }
        try {
            OAuthStateContext context = objectMapper.readValue(content, OAuthStateContext.class);
            if (context.provider() != provider || context.action() == null
                    || context.redirectUri() == null || context.redirectUri().isBlank()) {
                throw new ClientException(AuthErrorCode.OAUTH_STATE_INVALID);
            }
            return context;
        } catch (JsonProcessingException exception) {
            throw new ClientException(AuthErrorCode.OAUTH_STATE_INVALID);
        }
    }

    /**
     * 生成符合 RFC 7636 长度要求的 PKCE verifier。
     *
     * @return Base64URL 编码的随机 verifier
     */
    public String createPkceVerifier() {
        return randomValue(48);
    }

    /**
     * 生成 RFC 7636 S256 challenge。
     *
     * @param verifier 原始 verifier
     * @return S256 challenge
     */
    public String createPkceChallenge(String verifier) {
        try {
            return Base64.getUrlEncoder().withoutPadding().encodeToString(
                    MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII)));
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException("当前运行环境不支持 SHA-256", exception);
        }
    }

    /**
     * 使用密码学安全随机数生成 Base64URL 值。
     */
    private String randomValue(int bytesLength) {
        byte[] bytes = new byte[bytesLength];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
