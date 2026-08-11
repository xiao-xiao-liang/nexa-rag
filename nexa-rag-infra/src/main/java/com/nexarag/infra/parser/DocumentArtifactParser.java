package com.nexarag.infra.parser;

import com.nexarag.infra.parser.model.DocumentParseRequest;
import com.nexarag.infra.parser.model.ParsedArtifact;

/**
 * 文档解析产物生成器，负责将原始文档转换并持久化为可供后续切分的解析产物。
 *
 * <p>该接口负责项目的文件类型路由、对象存储制品和解析元数据；格式级的
 * {@code InputStream -> List<Document>} 解析由 Spring AI Alibaba 的
 * {@code com.alibaba.cloud.ai.document.DocumentParser} 在具体实现内部完成。</p>
 */
public interface DocumentArtifactParser {

    /**
     * 判断当前生成器是否支持本次解析请求。
     *
     * @param request 文档解析请求
     * @return true 表示支持，false 表示不支持
     */
    boolean supports(DocumentParseRequest request);

    /**
     * 解析原始文档并生成已持久化的解析产物。
     *
     * @param request 文档解析请求
     * @return 解析产物
     */
    ParsedArtifact parse(DocumentParseRequest request);
}
