package com.nexarag.document.service;

import com.nexarag.document.model.dto.UploadDocumentRequest;
import com.nexarag.document.model.vo.UploadDocumentResponse;
import org.springframework.web.multipart.MultipartFile;

/**
 * 文档上传服务，负责接收上传文件并提交文档入库流水线。
 */
public interface DocumentUploadService {

    /**
     * 上传文档并提交处理。
     *
     * @param knowledgeBaseId 知识库ID
     * @param file    上传文件
     * @param request 上传文档请求
     * @return 上传响应
     */
    UploadDocumentResponse upload(Long knowledgeBaseId, MultipartFile file, UploadDocumentRequest request);
}
