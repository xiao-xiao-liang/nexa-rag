package com.nexarag.document.service;

import com.nexarag.document.dto.IndexConfigRequest;
import com.nexarag.document.dto.ParseConfigRequest;
import com.nexarag.document.dto.ProcessDocumentRequest;
import com.nexarag.document.dto.SplitConfigRequest;
import com.nexarag.document.dto.UploadDocumentRequest;
import com.nexarag.document.enums.FileType;
import com.nexarag.document.enums.SplitStrategy;
import com.nexarag.infra.enums.ParserType;
import org.springframework.stereotype.Component;

/**
 * 文档处理配置默认值服务，负责把上传请求中的空配置补齐为本次处理快照。
 */
@Component
public class ProcessConfigDefaults {

    private static final int DEFAULT_CHUNK_SIZE = 1000;
    private static final int DEFAULT_CHUNK_OVERLAP = 100;

    /**
     * 合并上传请求配置和文件类型默认配置。
     *
     * @param fileType 上传文件类型
     * @param request  上传文档请求
     * @return 补齐默认值后的处理请求
     */
    public ProcessDocumentRequest merge(FileType fileType, UploadDocumentRequest request) {
        UploadDocumentRequest safeRequest = request == null
                ? new UploadDocumentRequest(null, null, null, null, null)
                : request;

        // 1. 根据文件类型补齐切分配置
        SplitConfigRequest splitConfig = mergeSplitConfig(fileType, safeRequest.splitConfig());

        // 2. 根据文件类型补齐解析配置
        ParseConfigRequest parseConfig = mergeParseConfig(fileType, safeRequest.parseConfig());

        // 3. 补齐索引配置，初版默认同时启用向量和关键词索引
        IndexConfigRequest indexConfig = mergeIndexConfig(safeRequest.indexConfig());
        return new ProcessDocumentRequest(splitConfig, parseConfig, indexConfig);
    }

    private SplitConfigRequest mergeSplitConfig(FileType fileType, SplitConfigRequest splitConfig) {
        SplitConfigRequest defaultConfig = defaultSplitConfig(fileType);
        if (splitConfig == null) {
            return defaultConfig;
        }
        if (splitConfig.splitStrategy() != null
                && splitConfig.chunkSize() != null
                && splitConfig.chunkOverlap() != null) {
            return splitConfig;
        }
        SplitStrategy splitStrategy = splitConfig.splitStrategy() == null
                ? defaultConfig.splitStrategy()
                : splitConfig.splitStrategy();
        Integer chunkSize = splitConfig.chunkSize() == null ? defaultConfig.chunkSize() : splitConfig.chunkSize();
        Integer chunkOverlap = splitConfig.chunkOverlap() == null
                ? defaultConfig.chunkOverlap()
                : splitConfig.chunkOverlap();
        return new SplitConfigRequest(splitStrategy, chunkSize, chunkOverlap);
    }

    private SplitConfigRequest defaultSplitConfig(FileType fileType) {
        SplitStrategy splitStrategy = switch (fileType) {
            case EXCEL -> SplitStrategy.EXCEL;
            case PPT, TEXT -> SplitStrategy.REGEX_TEXT;
            case PDF, WORD, MARKDOWN, UNKNOWN -> SplitStrategy.PARENT_MARKDOWN;
        };
        return new SplitConfigRequest(splitStrategy, DEFAULT_CHUNK_SIZE, DEFAULT_CHUNK_OVERLAP);
    }

    private ParseConfigRequest mergeParseConfig(FileType fileType, ParseConfigRequest parseConfig) {
        ParseConfigRequest defaultConfig = defaultParseConfig(fileType);
        if (parseConfig == null) {
            return defaultConfig;
        }
        if (parseConfig.parserType() != null
                && parseConfig.enableOcr() != null
                && parseConfig.enableImageDescription() != null) {
            return parseConfig;
        }
        ParserType parserType = parseConfig.parserType() == null ? defaultConfig.parserType() : parseConfig.parserType();
        Boolean enableOcr = parseConfig.enableOcr() == null ? defaultConfig.enableOcr() : parseConfig.enableOcr();
        Boolean enableImageDescription = parseConfig.enableImageDescription() == null
                ? defaultConfig.enableImageDescription()
                : parseConfig.enableImageDescription();
        return new ParseConfigRequest(parserType, enableOcr, enableImageDescription);
    }

    private ParseConfigRequest defaultParseConfig(FileType fileType) {
        return switch (fileType) {
            case PDF, WORD -> new ParseConfigRequest(ParserType.MINERU, true, false);
            case PPT, TEXT -> new ParseConfigRequest(ParserType.TIKA, false, false);
            case MARKDOWN, EXCEL, UNKNOWN -> new ParseConfigRequest(null, false, false);
        };
    }

    private IndexConfigRequest mergeIndexConfig(IndexConfigRequest indexConfig) {
        if (indexConfig == null) {
            return new IndexConfigRequest(true, true, true);
        }
        if (indexConfig.enabled() != null
                && indexConfig.vectorEnabled() != null
                && indexConfig.keywordEnabled() != null) {
            return indexConfig;
        }
        Boolean enabled = indexConfig.enabled() == null ? true : indexConfig.enabled();
        Boolean vectorEnabled = indexConfig.vectorEnabled() == null ? true : indexConfig.vectorEnabled();
        Boolean keywordEnabled = indexConfig.keywordEnabled() == null ? true : indexConfig.keywordEnabled();
        return new IndexConfigRequest(enabled, vectorEnabled, keywordEnabled);
    }
}
