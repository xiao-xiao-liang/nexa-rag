package com.nexarag.infra.parser.mineru;

import com.nexarag.common.error.BaseErrorCode;
import com.nexarag.common.exception.ServiceException;
import com.nexarag.infra.parser.model.*;
import com.nexarag.infra.parser.DocumentParser;
import com.nexarag.infra.constants.ParsedContentTypes;
import com.nexarag.infra.constants.ParserFileTypes;
import com.nexarag.infra.parser.mineru.client.MinerUClient;
import com.nexarag.infra.parser.mineru.extract.MarkdownImageUrlRewriter;
import com.nexarag.infra.parser.mineru.extract.MinerUExtractedResult;
import com.nexarag.infra.parser.mineru.extract.MinerUZipResultExtractor;
import com.nexarag.infra.parser.mineru.ratelimit.MinerUParseLimiter;
import com.nexarag.infra.storage.ObjectNameResolver;
import com.nexarag.infra.storage.StoredFile;
import com.nexarag.infra.storage.service.FileStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * MinerU 文档解析器，负责将 PDF 和 Word 文件解析为标准 Markdown 产物。
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "nexa.parser.mineru", name = "enabled", havingValue = "true", matchIfMissing = true)
public class MinerUDocumentParser implements DocumentParser {

    private final FileStorageService fileStorageService;
    private final ObjectNameResolver objectNameResolver;
    private final MinerUClient minerUClient;
    private final MinerUZipResultExtractor zipResultExtractor;
    private final MarkdownImageUrlRewriter imageUrlRewriter;
    private final MinerUParseLimiter minerUParseLimiter;

    /**
     * 判断当前请求是否应交由 MinerU 解析。
     *
     * @param request 文档解析请求
     * @return true 表示支持 PDF 或 Word
     */
    @Override
    public boolean supports(DocumentParseRequest request) {
        return request != null
                && (ParserFileTypes.PDF.equals(request.fileType()) || ParserFileTypes.WORD.equals(request.fileType()));
    }

    /**
     * 调用 MinerU 解析原始文件，并保存解析后的 Markdown 与图片资源。
     *
     * @param request 文档解析请求
     * @return 文档解析结果
     */
    @Override
    public DocumentParseResult parse(DocumentParseRequest request) {
        return minerUParseLimiter.execute(request.documentId(), () -> doParse(request));
    }

    private DocumentParseResult doParse(DocumentParseRequest request) {
        try (InputStream originalInputStream = fileStorageService.load(request.originalObjectName())) {
            // 1. 调用 MinerU 获取 ZIP 格式解析产物
            MinerUParseResponse response = minerUClient.parse(MinerUParseCommand.builder()
                    .documentId(request.documentId())
                    .fileName(request.originalFileName())
                    .inputStream(originalInputStream)
                    .enableOcr(Boolean.TRUE.equals(request.enableOcr()))
                    .build());

            // 2. 提取 Markdown 主文件和图片资源
            MinerUExtractedResult extractedResult = zipResultExtractor.extract(response.zipInputStream());

            // 3. 上传图片资源并建立相对路径到访问地址的映射
            Map<String, String> assetUrls = saveAssetFiles(request.documentId(), extractedResult);

            // 4. 重写 Markdown 图片地址并保存标准 Markdown 产物
            String rewrittenMarkdown = imageUrlRewriter.rewrite(extractedResult.markdownContent(), assetUrls);
            byte[] markdownBytes = rewrittenMarkdown.getBytes(StandardCharsets.UTF_8);
            String parsedObjectName = objectNameResolver.resolveParsedObjectName(
                    request.documentId(), request.originalFileName(), ".md");
            StoredFile storedFile = fileStorageService.saveAs(parsedObjectName,
                    new ByteArrayInputStream(markdownBytes), markdownBytes.length, ParsedContentTypes.TEXT_MARKDOWN);

            // 5. 合并解析元数据并返回解析结果
            return DocumentParseResult.builder()
                    .contentType(ParsedContentTypes.TEXT_MARKDOWN)
                    .content(rewrittenMarkdown)
                    .parsedObjectName(storedFile.objectName())
                    .parsedFileUrl(storedFile.url())
                    .metadata(buildMetadata(response, extractedResult, assetUrls))
                    .build();
        } catch (ServiceException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new ServiceException("MinerU解析文档失败，documentId=" + request.documentId(),
                    exception, BaseErrorCode.SERVICE_ERROR);
        }
    }

    private Map<String, String> saveAssetFiles(Long documentId, MinerUExtractedResult extractedResult) {
        Map<String, String> assetUrls = new LinkedHashMap<>();
        for (MinerUAssetFile assetFile : extractedResult.assetFiles()) {
            String assetObjectName = objectNameResolver.resolveParsedAssetObjectName(documentId, assetFile.fileName());
            StoredFile storedFile = fileStorageService.saveAs(assetObjectName,
                    new ByteArrayInputStream(assetFile.content()), assetFile.content().length,
                    resolveImageContentType(assetFile.fileName()));
            assetUrls.put(assetFile.relativePath(), storedFile.url());
        }
        return assetUrls;
    }

    private String resolveImageContentType(String fileName) {
        String lowerFileName = fileName == null ? "" : fileName.toLowerCase();
        if (lowerFileName.endsWith(".jpg") || lowerFileName.endsWith(".jpeg")) {
            return "image/jpeg";
        }
        if (lowerFileName.endsWith(".webp")) {
            return "image/webp";
        }
        if (lowerFileName.endsWith(".gif")) {
            return "image/gif";
        }
        return "image/png";
    }

    private Map<String, Object> buildMetadata(MinerUParseResponse response,
                                              MinerUExtractedResult extractedResult,
                                              Map<String, String> assetUrls) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("parser", "mineru");
        metadata.put("markdownFileName", extractedResult.markdownFileName());
        metadata.put("assetCount", assetUrls.size());
        if (extractedResult.metadata() != null) {
            metadata.putAll(extractedResult.metadata());
        }
        if (response.metadata() != null) {
            metadata.putAll(response.metadata());
        }
        return metadata;
    }
}
