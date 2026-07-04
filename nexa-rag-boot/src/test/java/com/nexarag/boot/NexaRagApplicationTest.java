package com.nexarag.boot;

import com.nexarag.document.mapper.DocumentChunkMapper;
import com.nexarag.document.mapper.DocumentMapper;
import com.nexarag.model.mapper.ModelCallLogMapper;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * NexaRAG 应用启动测试。
 */
@SpringBootTest(properties = {
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration"
})
class NexaRagApplicationTest {

    @MockitoBean
    private DocumentMapper documentMapper;

    @MockitoBean
    private DocumentChunkMapper documentChunkMapper;

    @MockitoBean
    private ModelCallLogMapper modelCallLogMapper;

    @Test
    void contextLoads() {
    }
}
