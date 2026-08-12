package com.nexarag.infra.parser.service;

import com.nexarag.infra.parser.model.DocumentArtifactDTO;
import com.nexarag.infra.parser.model.DocumentParseRequest;
import com.nexarag.infra.parser.model.ParsedArtifact;
import com.nexarag.infra.parser.model.StagedDocumentBO;

/**
 * 文档解析服务，负责根据文件类型选择解析器并执行解析。
 */
public interface DocumentParseService {

    /**
     * 解析文档并返回解析结果。
     *
     * @param request 文档解析请求
     * @return 文档解析结果
     */
    ParsedArtifact parse(DocumentParseRequest request);

    /**
     * 处理已由调用方写入受管工作区的原始文档。
     *
     * @param artifactDTO 文档制品处理上下文
     * @param stagedDocumentBO 已暂存原始文档
     * @return 已发布的解析制品
     */
    ParsedArtifact parseStaged(DocumentArtifactDTO artifactDTO, StagedDocumentBO stagedDocumentBO);
}
