package com.nexarag.infra.parser.passthrough;

import com.nexarag.infra.parser.model.DocumentParseRequest;
import com.nexarag.infra.parser.model.ParsedArtifact;
import com.nexarag.infra.parser.DocumentArtifactParser;
import com.nexarag.infra.constants.ParsedContentTypes;
import com.nexarag.infra.constants.ParserFileTypes;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 透传文档解析器，用于 Markdown 和 Excel 这类无需在解析阶段转换内容的文件。
 */
@Component
@ConditionalOnProperty(prefix = "nexa.parser.passthrough", name = "enabled", havingValue = "true", matchIfMissing = true)
public class PassthroughArtifactParser implements DocumentArtifactParser {

    /**
     * 判断是否支持透传解析。
     *
     * @param request 文档解析请求
     * @return true 表示支持透传解析
     */
    @Override
    public boolean supports(DocumentParseRequest request) {
        return request != null
                && (ParserFileTypes.MARKDOWN.equals(request.fileType())
                || ParserFileTypes.EXCEL.equals(request.fileType()));
    }

    /**
     * 返回原始文件作为解析产物。
     *
     * @param request 文档解析请求
     * @return 文档解析结果
     */
    @Override
    public ParsedArtifact parse(DocumentParseRequest request) {
        // 1. 根据文件类型确定透传产物内容类型
        String contentType = ParserFileTypes.MARKDOWN.equals(request.fileType())
                ? ParsedContentTypes.TEXT_MARKDOWN
                : ParsedContentTypes.EXCEL;

        // 2. 复用原始文件地址作为解析产物地址
        return ParsedArtifact.builder()
                .contentType(contentType)
                .objectKey(request.originalObjectName())
                .metadata(Map.of(
                        "parser", "passthrough",
                        "passthrough", true,
                        "originalFileName", request.originalFileName()
                ))
                .build();
    }
}
