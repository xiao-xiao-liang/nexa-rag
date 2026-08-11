package com.nexarag.infra.source;

import com.nexarag.infra.enums.ExternalDocumentSourceType;
import com.nexarag.infra.source.model.SourceReadRequestDTO;
import com.nexarag.infra.source.model.SourceReadResultBO;

/**
 * 外部平台内容读取扩展点；实现仅负责平台协议，不负责对象存储和工作流状态。
 */
public interface ExternalDocumentSourceReader {

    boolean supports(ExternalDocumentSourceType sourceType);

    String validateAndExtractDocumentId(String sourceUrl);

    SourceReadResultBO read(SourceReadRequestDTO request);
}
