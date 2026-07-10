package com.nexarag.infra.parser.tika;

import com.nexarag.common.error.BaseErrorCode;
import com.nexarag.common.exception.ServiceException;
import com.nexarag.infra.parser.model.DocumentParseRequest;
import com.nexarag.infra.parser.model.DocumentParseResult;
import com.nexarag.infra.parser.DocumentParser;
import com.nexarag.infra.constants.ParsedContentTypes;
import com.nexarag.infra.constants.ParserFileTypes;
import com.nexarag.infra.storage.ObjectNameResolver;
import com.nexarag.infra.storage.StoredFile;
import com.nexarag.infra.storage.service.FileStorageService;
import lombok.RequiredArgsConstructor;
import org.apache.tika.Tika;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Tika 文档解析器，负责将 PPT 和纯文本文件抽取为标准文本产物。
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "nexa.parser.tika", name = "enabled", havingValue = "true", matchIfMissing = true)
public class TikaDocumentParser implements DocumentParser {

    private final FileStorageService fileStorageService;
    private final ObjectNameResolver objectNameResolver;
    private final Tika tika = new Tika();

    /**
     * 判断是否支持 Tika 解析。
     *
     * @param request 文档解析请求
     * @return true 表示支持
     */
    @Override
    public boolean supports(DocumentParseRequest request) {
        return request != null
                && (ParserFileTypes.PPT.equals(request.fileType()) || ParserFileTypes.TEXT.equals(request.fileType()));
    }

    /**
     * 使用 Tika 抽取文本并保存解析产物。
     *
     * @param request 文档解析请求
     * @return 文档解析结果
     */
    @Override
    public DocumentParseResult parse(DocumentParseRequest request) {
        try (InputStream inputStream = fileStorageService.load(request.originalObjectName())) {
            // 1. 使用 Tika 抽取文本内容
            String content = tika.parseToString(inputStream).trim();
            if (!StringUtils.hasText(content)) {
                throw new ServiceException("Tika解析结果为空，documentId=" + request.documentId());
            }

            // 2. 保存标准文本解析产物
            byte[] contentBytes = content.getBytes(StandardCharsets.UTF_8);
            String parsedObjectName = objectNameResolver.resolveParsedObjectName(
                    request.documentId(), request.originalFileName(), ".txt");
            StoredFile storedFile = fileStorageService.saveAs(parsedObjectName,
                    new ByteArrayInputStream(contentBytes), contentBytes.length, ParsedContentTypes.TEXT_PLAIN);

            // 3. 组装解析结果
            return DocumentParseResult.builder()
                    .contentType(ParsedContentTypes.TEXT_PLAIN)
                    .content(content)
                    .parsedObjectName(storedFile.objectName())
                    .parsedFileUrl(storedFile.url())
                    .metadata(Map.of(
                            "parser", "tika",
                            "originalFileName", request.originalFileName(),
                            "textLength", content.length()
                    ))
                    .build();
        } catch (ServiceException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new ServiceException("Tika解析文档失败，documentId=" + request.documentId(),
                    exception, BaseErrorCode.SERVICE_ERROR);
        }
    }
}