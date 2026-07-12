package com.nexarag.boot;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * NexaRAG 应用启动类。
 */
@MapperScan("com.nexarag.**.mapper")
@EnableScheduling
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
