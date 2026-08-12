package com.nexarag.document.service.impl;

import com.nexarag.common.exception.ClientException;
import com.nexarag.document.constants.DocumentConstants;
import com.nexarag.document.enums.DocumentErrorCode;
import com.nexarag.document.enums.DocumentStatus;
import com.nexarag.document.enums.FileType;
import com.nexarag.document.model.dto.ExternalDocumentSubmitDTO;
import com.nexarag.document.model.dto.UploadDocumentRequest;
import com.nexarag.document.model.vo.UploadDocumentResponse;
import com.nexarag.document.service.DocumentPipelineSubmitService;
import com.nexarag.document.service.ProcessConfigDefaults;
import com.nexarag.infra.enums.ExternalDocumentSourceType;
import com.nexarag.infra.source.ExternalDocumentSourceService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import static com.nexarag.document.constants.DocumentConstants.DEFAULT_EXTERNAL_DOCUMENT_TITLE;

/** 外部文档统一受理服务，仅创建文档任务，不在请求线程读取远端内容。 */
@Service
@RequiredArgsConstructor
public class ExternalDocumentSubmitServiceImpl {

    private final ExternalDocumentSourceService externalDocumentSourceService;
    private final DocumentPipelineSubmitService documentPipelineSubmitService;
    private final ProcessConfigDefaults processConfigDefaults;

    public UploadDocumentResponse submit(ExternalDocumentSubmitDTO request) {
        // 1. 校验来源类型和 URL，并仅提取临时定位信息
        if (request.sourceType() == ExternalDocumentSourceType.LOCAL) {
            throw new ClientException("本地文件请使用上传接口", DocumentErrorCode.DOCUMENT_UPLOAD_FILE_INVALID);
        }
        externalDocumentSourceService.validateAndExtractDocumentId(request.sourceType(), request.sourceUrl());

        // 2. 创建外部来源文档并通过既有事务写入 Outbox
        String title = request.title() == null || request.title().isBlank()
                ? DEFAULT_EXTERNAL_DOCUMENT_TITLE : request.title();
        var document = documentPipelineSubmitService.createAndSubmit(
                com.nexarag.document.model.dto.CreateDocumentRequest.external(title, request.description(),
                        "external.md", request.sourceType(), request.sourceUrl()),
                processConfigDefaults.merge(FileType.MARKDOWN, new UploadDocumentRequest(null, null,
                        request.splitConfig(), request.parseConfig(), request.indexConfig())));
        return new UploadDocumentResponse(document.getDocumentId(), document.getProcessId(), document.getStatus());
    }
}
