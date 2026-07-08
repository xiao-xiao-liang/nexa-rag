package com.nexarag.retrieval.dto;

import java.util.List;

/**
 * 文档索引结果，用于向 Workflow 返回一次文档索引流水线的执行摘要。
 *
 * @param documentId        文档ID
 * @param success           是否成功
 * @param totalChunkCount   片段总数
 * @param indexedChunkCount 已索引片段数
 * @param skippedChunkCount 跳过索引片段数
 * @param failedChunkCount  失败片段数
 * @param vectorEnabled     是否启用向量索引
 * @param keywordEnabled    是否启用关键词索引
 * @param failureReason     失败原因
 * @param chunks            片段索引结果
 */
public record DocumentIndexResult(Long documentId,
                                  boolean success,
                                  int totalChunkCount,
                                  int indexedChunkCount,
                                  int skippedChunkCount,
                                  int failedChunkCount,
                                  boolean vectorEnabled,
                                  boolean keywordEnabled,
                                  String failureReason,
                                  List<DocumentChunkIndexResult> chunks) {
}