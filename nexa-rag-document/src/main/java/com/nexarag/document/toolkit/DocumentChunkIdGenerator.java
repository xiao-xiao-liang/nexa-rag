package com.nexarag.document.toolkit;

import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * 文档片段ID生成器，集中管理 chunk 业务ID格式。
 */
@Component
public class DocumentChunkIdGenerator {

    /**
     * 生成新的片段ID。
     *
     * @param documentId 文档ID
     * @return 片段ID
     */
    public String nextChunkId(Long documentId) {
        return UUID.randomUUID().toString();
    }
}
