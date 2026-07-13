package com.nexarag.retrieval.service;

import com.nexarag.retrieval.model.IndexConfigSnapshot;
import com.nexarag.retrieval.model.ChunkEmbedding;
import com.nexarag.retrieval.model.IndexableChunk;

import java.util.List;

/**
 * 片段向量化服务，负责把可索引片段转换为向量。
 */
public interface EmbeddingService {

    /**
     * 批量生成片段向量。
     *
     * @param chunks 待向量化片段
     * @param config 索引运行配置
     * @return 片段向量列表
     */
    List<ChunkEmbedding> embed(List<IndexableChunk> chunks, IndexConfigSnapshot config);
}