package com.nexarag.retrieval.embedding;

import com.nexarag.model.gateway.ModelGateway;
import com.nexarag.model.gateway.embedding.EmbeddingModelRequest;
import com.nexarag.model.gateway.embedding.EmbeddingModelResponse;
import com.nexarag.retrieval.config.RetrievalProperties;
import com.nexarag.retrieval.model.ChunkEmbedding;
import com.nexarag.retrieval.model.IndexableChunk;
import com.nexarag.retrieval.service.impl.EmbeddingServiceImpl;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 模型网关片段向量化服务测试。
 */
class EmbeddingServiceImplTest {

    @Test
    void embedShouldSplitChunksByMaxBatchSize() {
        ModelGateway modelGateway = mock(ModelGateway.class);
        RetrievalProperties properties = new RetrievalProperties();
        properties.getEmbedding().setMaxBatchSize(10);
        EmbeddingServiceImpl service = new EmbeddingServiceImpl(modelGateway, properties);
        when(modelGateway.embedding(any(EmbeddingModelRequest.class))).thenAnswer(invocation -> {
            EmbeddingModelRequest request = invocation.getArgument(0);
            List<float[]> embeddings = request.texts().stream()
                    .map(text -> new float[]{1.0F, 2.0F})
                    .toList();
            return new EmbeddingModelResponse(embeddings, "embedding-test", 0);
        });

        // 1. 构造超过百炼单次批量限制的片段数量
        List<IndexableChunk> chunks = IntStream.range(0, 49)
                .mapToObj(index -> new IndexableChunk("chunk-" + index, 1L, index, null,
                        "测试文本" + index, null, 1))
                .toList();

        // 2. 执行向量化并捕获模型网关请求
        List<ChunkEmbedding> embeddings = service.embed(chunks, null);

        // 3. 验证按 10/10/10/10/9 分批调用，且结果顺序保持不变
        ArgumentCaptor<EmbeddingModelRequest> captor = ArgumentCaptor.forClass(EmbeddingModelRequest.class);
        verify(modelGateway, times(5)).embedding(captor.capture());
        assertThat(captor.getAllValues())
                .extracting(request -> request.texts().size())
                .containsExactly(10, 10, 10, 10, 9);
        assertThat(embeddings).hasSize(49);
        assertThat(embeddings.getFirst().chunkId()).isEqualTo("chunk-0");
        assertThat(embeddings.getLast().chunkId()).isEqualTo("chunk-48");
    }
}
