package com.nexarag.auth.runner;

import com.nexarag.auth.service.AuthIdentityBloomRebuildService;
import com.nexarag.common.exception.ServiceException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** 由一次性启动参数触发的认证 BloomFilter 低峰重建入口。 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "nexa.auth.identity-bloom", name = "rebuild-version")
public class AuthIdentityBloomRebuildRunner implements ApplicationRunner {

    private final int rebuildVersion;
    private final AuthIdentityBloomRebuildService rebuildService;

    public AuthIdentityBloomRebuildRunner(
            @Value("${nexa.auth.identity-bloom.rebuild-version}") int rebuildVersion,
            AuthIdentityBloomRebuildService rebuildService) {
        this.rebuildVersion = rebuildVersion;
        this.rebuildService = rebuildService;
    }

    /**
     * 启动时重建指定版本。重建服务会先删除 Redis 就绪标记，使认证路径自动回退数据库。
     *
     * @param args 应用启动参数
     */
    @Override
    public void run(ApplicationArguments args) {
        if (rebuildVersion <= 0) {
            throw new ServiceException("认证 BloomFilter 重建版本必须为正整数");
        }
        rebuildService.rebuild(rebuildVersion);
        log.info("认证 BloomFilter 单次重建已完成，version={}", rebuildVersion);
    }
}
