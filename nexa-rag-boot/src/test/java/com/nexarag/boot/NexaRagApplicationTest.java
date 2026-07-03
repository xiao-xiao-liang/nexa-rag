package com.nexarag.boot;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * NexaRAG 应用启动测试。
 */
@SpringBootTest(properties = {
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration"
})
class NexaRagApplicationTest {

    @Test
    void contextLoads() {
    }
}
