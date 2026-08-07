package com.nexarag.boot;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * NexaRAG 应用启动类。
 */
@MapperScan({
        "com.nexarag.document.mapper",
        "com.nexarag.model.mapper",
        "com.nexarag.chat.mapper"
})
@EnableScheduling
@EnableAsync
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
