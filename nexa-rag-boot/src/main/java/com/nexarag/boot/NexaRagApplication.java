package com.nexarag.boot;

import com.nexarag.auth.config.BootstrapAdministratorProperties;
import com.nexarag.auth.config.IpLocationProperties;
import com.nexarag.auth.config.OAuthProviderProperties;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * NexaRAG 应用启动类。
 */
@MapperScan({
        "com.nexarag.auth.mapper",
        "com.nexarag.document.mapper",
        "com.nexarag.model.mapper",
        "com.nexarag.chat.mapper"
})
@EnableScheduling
@EnableAsync
@EnableConfigurationProperties({BootstrapAdministratorProperties.class, IpLocationProperties.class,
        OAuthProviderProperties.class})
@SpringBootApplication(scanBasePackages = "com.nexarag")
public class NexaRagApplication {

    /**
     * 启动 NexaRAG 应用。
     *
     * @param args 启动参数
     */
    public static void main(String[] args) {
        SpringApplication.run(NexaRagApplication.class, args);
    }
}
