package com.nexarag.document.service;

/**
 * 文档流水线执行器，后续由 Workflow Graph 实现真实解析、切分和索引流程。
 */
public interface DocumentPipelineExecutor {

    /**
     * 执行指定文档的入库流水线。
     *
     * @param documentId 文档ID
     */
    void execute(Long documentId);
}