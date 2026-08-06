package com.nexarag.document.splitter;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import org.springframework.stereotype.Component;

/**
 * 文档章节ID生成器，集中管理章节持久化所需的数值型业务ID。
 */
@Component
public class DocumentSectionIdGenerator {

    /**
     * 生成新的章节ID。
     *
     * @param documentId 文档ID，用于保持调用上下文与片段ID生成器一致
     * @return 章节ID
     */
    public Long nextSectionId(Long documentId) {
        return IdWorker.getId();
    }
}
