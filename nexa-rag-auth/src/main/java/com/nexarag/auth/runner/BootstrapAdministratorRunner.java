package com.nexarag.auth.runner;

import com.nexarag.auth.service.BootstrapAdministratorService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * 应用启动后执行默认管理员的幂等初始化。
 */
@Component
@RequiredArgsConstructor
public class BootstrapAdministratorRunner implements ApplicationRunner {

    private final BootstrapAdministratorService bootstrapAdministratorService;

    /**
     * {@inheritDoc}
     */
    @Override
    public void run(ApplicationArguments args) {
        bootstrapAdministratorService.initializeIfEnabled();
    }
}
