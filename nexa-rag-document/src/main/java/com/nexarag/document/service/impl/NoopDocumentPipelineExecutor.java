package com.nexarag.document.service.impl;

import com.nexarag.document.service.DocumentPipelineExecutor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 占位文档流水线执行器，当前批次只打通 Worker 编排链路，不推进真实处理阶段。
 */
@Slf4j
@Service
public class NoopDocumentPipelineExecutor implements DocumentPipelineExecutor {

    /**
     * 执行占位文档流水线。
     *
     * @param documentId 文档ID
     */
    @Override
    public void execute(Long documentId) {
        // 1. 当前批次只确认 Worker 能够调用流水线入口，真实阶段由后续 Workflow 实现
        log.info("文档流水线占位执行完成，documentId={}", documentId);
    }
}