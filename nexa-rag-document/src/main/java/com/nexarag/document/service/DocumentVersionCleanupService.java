package com.nexarag.document.service;

/**
 * 文档版本永久删除数据清理服务，在版本级外部资源清理完成后物理移除版本派生数据。
 */
public interface DocumentVersionCleanupService {

    /**
     * 物理删除指定删除中版本的片段、章节和版本快照；已删除时幂等返回。
     */
    void cleanup(Long documentId, Long documentVersionId);
}
