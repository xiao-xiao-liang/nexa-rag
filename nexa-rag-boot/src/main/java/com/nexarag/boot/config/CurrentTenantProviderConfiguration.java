package com.nexarag.boot.config;

import com.nexarag.auth.tenant.CurrentTenantService;
import com.nexarag.document.tenant.CurrentTenantProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * 在完整应用中以 Sa-Token Token-Session 的当前工作空间替换 document 模块的固定默认租户。
 */
@Configuration
public class CurrentTenantProviderConfiguration {

    /**
     * 返回会在每次知识库访问前复验成员资格的当前租户提供者。
     *
     * @param currentTenantService 当前工作空间服务
     * @return 动态当前租户提供者
     */
    @Bean
    @Primary
    public CurrentTenantProvider currentTenantProvider(CurrentTenantService currentTenantService) {
        return currentTenantService::getRequiredCurrentTenantId;
    }
}
