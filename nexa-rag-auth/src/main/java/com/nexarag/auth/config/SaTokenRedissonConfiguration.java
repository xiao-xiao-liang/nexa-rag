package com.nexarag.auth.config;

import cn.dev33.satoken.SaManager;
import cn.dev33.satoken.dao.SaTokenDao;
import cn.dev33.satoken.dao.SaTokenDaoForRedisson;
import org.redisson.api.RedissonClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Sa-Token 直连 Redisson 的会话存储配置，避免经由 Spring Data Redis 适配层更新会话。
 */
@Configuration
public class SaTokenRedissonConfiguration {

    /**
     * 创建并注册 Sa-Token 的 Redisson DAO。
     *
     * @param redissonClient 项目统一管理的 Redisson 客户端
     * @return Sa-Token 会话存储 DAO
     */
    @Bean
    public SaTokenDao saTokenDao(RedissonClient redissonClient) {
        // 1. 复用现有 Redisson 客户端，保证认证会话与业务锁使用同一 Redis 配置
        SaTokenDao saTokenDao = new SaTokenDaoForRedisson(redissonClient);

        // 2. 显式注册全局 DAO，阻止 Sa-Token 回退到默认内存实现
        SaManager.setSaTokenDao(saTokenDao);
        return saTokenDao;
    }
}
