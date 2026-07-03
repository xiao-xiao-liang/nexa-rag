package com.nexarag.boot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * NexaRAG 应用启动类。
 */
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
