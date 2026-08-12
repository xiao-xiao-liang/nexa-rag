package com.nexarag.infra.source;

import com.nexarag.infra.enums.ExternalDocumentSourceType;
import com.nexarag.infra.parser.workspace.ArtifactWorkspace;
import com.nexarag.infra.source.model.SourceReadRequestDTO;
import com.nexarag.infra.source.model.SourceReadResultBO;

/**
 * 外部平台内容读取扩展点；实现仅负责平台协议，不负责对象存储和工作流状态。
 */
public interface ExternalDocumentSourceReader {

    boolean supports(ExternalDocumentSourceType sourceType);

    String validateAndExtractDocumentId(String sourceUrl);

    /**
     * 将外部来源原始内容写入调用方提供的受管工作区。
     *
     * @param request 外部来源读取请求
     * @param workspace 当前任务工作区
     * @return 文件化读取结果
     */
    SourceReadResultBO read(SourceReadRequestDTO request, ArtifactWorkspace workspace);
}
