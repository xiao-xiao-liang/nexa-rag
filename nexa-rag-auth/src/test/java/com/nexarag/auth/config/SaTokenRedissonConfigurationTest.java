package com.nexarag.auth.config;

import cn.dev33.satoken.SaManager;
import cn.dev33.satoken.dao.SaTokenDao;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Sa-Token 直连 Redisson DAO 装配测试。
 */
class SaTokenRedissonConfigurationTest {

    private static final String CONFIGURATION_CLASS_NAME = "com.nexarag.auth.config.SaTokenRedissonConfiguration";
    private static final String REDISSON_CLIENT_CLASS_NAME = "org.redisson.api.RedissonClient";
    private static final String REDISSON_DAO_CLASS_NAME = "cn.dev33.satoken.dao.SaTokenDaoForRedisson";

    /**
     * 每个测试后清理 Sa-Token 的静态 DAO，避免影响其他认证测试。
     */
    @AfterEach
    void resetSaTokenDao() {
        SaManager.setSaTokenDao(null);
    }

    /**
     * 应使用直连 Redisson 的 DAO，并注册为 Sa-Token 全局 DAO。
     *
     * @throws ClassNotFoundException 找不到直连 Redisson 相关类型时抛出
     */
    @Test
    @SuppressWarnings({ "rawtypes", "unchecked" })
    void shouldRegisterDirectRedissonDao() throws ClassNotFoundException {
        Class<?> configurationClass = Class.forName(CONFIGURATION_CLASS_NAME);
        Class redissonClientClass = Class.forName(REDISSON_CLIENT_CLASS_NAME);
        Object redissonClient = mock(redissonClientClass);

        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.registerBean(redissonClientClass, () -> redissonClient);
            context.register(configurationClass);
            context.refresh();

            SaTokenDao saTokenDao = context.getBean(SaTokenDao.class);
            assertThat(saTokenDao.getClass().getName()).isEqualTo(REDISSON_DAO_CLASS_NAME);
            assertThat(SaManager.getSaTokenDao()).isSameAs(saTokenDao);
        }
    }
}
