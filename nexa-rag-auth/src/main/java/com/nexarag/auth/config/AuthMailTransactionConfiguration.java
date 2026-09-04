package com.nexarag.auth.config;

import com.nexarag.auth.mail.AuthMailMessageCipher;
import com.nexarag.common.exception.ServiceException;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 装配认证邮件消息的加密能力。
 */
@Configuration
@EnableConfigurationProperties(AuthMailProperties.class)
public class AuthMailTransactionConfiguration {

    /**
     * 创建消息载荷加密器，禁止使用硬编码或默认密钥。
     *
     * @param properties 认证邮件消息配置
     * @return AES-GCM 加密器
     */
    @Bean
    public AuthMailMessageCipher authMailMessageCipher(AuthMailProperties properties) {
        if (properties.getMessageMasterKey() == null || properties.getMessageMasterKey().isBlank()) {
            throw new ServiceException("认证邮件消息密钥未配置");
        }
        return new AuthMailMessageCipher(properties.getMessageMasterKey());
    }
}
