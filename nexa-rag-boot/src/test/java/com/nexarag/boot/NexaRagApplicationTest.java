package com.nexarag.boot;

import com.nexarag.document.mapper.DocumentChunkMapper;
import com.nexarag.document.mapper.DocumentMapper;
import com.nexarag.document.outbox.mapper.DocumentPipelineOutboxMapper;
import com.nexarag.infra.messaging.document.DocumentPipelineMessagePublisher;
import com.nexarag.model.mapper.ModelCallLogMapper;
import com.nexarag.model.mapper.ModelConfigMapper;
import com.nexarag.model.mapper.ModelGovernanceConfigMapper;
import com.nexarag.model.mapper.ModelRegistryVersionMapper;
import com.nexarag.model.mapper.ModelRouteConfigMapper;
import com.nexarag.model.mapper.ModelRouteMapper;
import com.nexarag.retrieval.index.keyword.KeywordIndexClient;
import com.nexarag.retrieval.index.vector.VectorIndexClient;
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
    private DocumentPipelineOutboxMapper documentPipelineOutboxMapper;

    @MockitoBean
    private DocumentPipelineMessagePublisher documentPipelineMessagePublisher;

    @MockitoBean
    private ModelCallLogMapper modelCallLogMapper;

    @MockitoBean
    private ModelConfigMapper modelConfigMapper;

    @MockitoBean
    private ModelRegistryVersionMapper modelRegistryVersionMapper;

    @MockitoBean
    private ModelGovernanceConfigMapper modelGovernanceConfigMapper;

    @MockitoBean
    private ModelRouteMapper modelRouteMapper;

    @MockitoBean
    private ModelRouteConfigMapper modelRouteConfigMapper;

    @MockitoBean
    private VectorIndexClient vectorIndexClient;

    @MockitoBean
    private KeywordIndexClient keywordIndexClient;

    @Test
    void contextLoads() {
    }
}
