package com.nexarag.infra.source;

import com.nexarag.infra.enums.ExternalDocumentSourceType;
import com.nexarag.infra.source.model.SourceArtifactBO;
import com.nexarag.infra.source.model.SourceReadRequestDTO;

/**
 * 外部来源读取编排服务，负责路由 Reader 并持久化来源制品。
 */
public interface ExternalDocumentSourceService {

    String validateAndExtractDocumentId(ExternalDocumentSourceType sourceType, String sourceUrl);

    SourceArtifactBO readAndPersist(SourceReadRequestDTO request);
}
