package com.nexarag.document.service;

import com.nexarag.document.dto.ProcessDocumentRequest;
import com.nexarag.document.vo.DocumentProcessStatusVO;

/**
 * 文档流水线触发服务，负责将文档处理状态提交和队列投递组合为一个对外能力。
 */
public interface DocumentPipelineTriggerService {

    /**
     * 提交文档处理并投递流水线任务。
     *
     * @param documentId 文档ID
     * @param request    文档处理请求
     * @return 文档处理状态和实时队列信息
     */
    DocumentProcessStatusVO submitProcess(Long documentId, ProcessDocumentRequest request);

    /**
     * 重试失败文档并投递流水线任务。
     *
     * @param documentId 文档ID
     * @return 文档处理状态和实时队列信息
     */
    DocumentProcessStatusVO retryProcess(Long documentId);
}
