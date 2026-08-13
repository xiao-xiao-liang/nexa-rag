package com.nexarag.document.service;

import com.nexarag.document.model.dto.IndexConfigRequest;
import com.nexarag.document.enums.ExcelSplitMode;
import com.nexarag.document.model.dto.ExcelSplitOptions;
import com.nexarag.document.model.dto.MarkdownSplitOptions;
import com.nexarag.document.model.dto.ParseConfigRequest;
import com.nexarag.document.model.dto.ProcessDocumentRequest;
import com.nexarag.document.model.dto.RegexSplitOptions;
import com.nexarag.document.model.dto.SplitConfigRequest;
import com.nexarag.document.model.dto.UploadDocumentRequest;
import com.nexarag.document.enums.FileType;
import com.nexarag.document.enums.SplitStrategy;
import org.springframework.stereotype.Component;

/**
 * 文档处理配置默认值服务，负责把上传请求中的空配置补齐为本次处理快照。
 */
@Component
public class ProcessConfigDefaults {

    private static final int DEFAULT_CHUNK_SIZE = 1000;
    private static final int DEFAULT_CHUNK_OVERLAP = 100;
    private static final int DEFAULT_MARKDOWN_TITLE_LEVEL = 3;
    private static final String DEFAULT_TEXT_SEPARATOR = "\n\n";

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
        SplitStrategy splitStrategy = splitConfig.splitStrategy() == null
                ? defaultConfig.splitStrategy()
                : splitConfig.splitStrategy();
        Integer chunkSize = splitConfig.chunkSize() == null ? defaultConfig.chunkSize() : splitConfig.chunkSize();
        Integer chunkOverlap = splitConfig.chunkOverlap() == null
                ? defaultConfig.chunkOverlap()
                : splitConfig.chunkOverlap();
        MarkdownSplitOptions markdown = mergeMarkdownOptions(splitConfig.markdown(), defaultConfig.markdown());
        RegexSplitOptions regex = mergeRegexOptions(splitConfig.regex(), defaultConfig.regex());
        ExcelSplitOptions excel = mergeExcelOptions(splitConfig.excel(), defaultConfig.excel());
        return new SplitConfigRequest(splitStrategy, chunkSize, chunkOverlap, markdown, regex, excel);
    }

    private SplitConfigRequest defaultSplitConfig(FileType fileType) {
        SplitStrategy splitStrategy = switch (fileType) {
            case EXCEL -> SplitStrategy.EXCEL;
            case PPT, TEXT -> SplitStrategy.REGEX_TEXT;
            case PDF, WORD, MARKDOWN, UNKNOWN -> SplitStrategy.PARENT_MARKDOWN;
        };
        return new SplitConfigRequest(splitStrategy, DEFAULT_CHUNK_SIZE, DEFAULT_CHUNK_OVERLAP,
                defaultMarkdownOptions(), defaultRegexOptions(), defaultExcelOptions());
    }

    private MarkdownSplitOptions mergeMarkdownOptions(MarkdownSplitOptions options, MarkdownSplitOptions defaults) {
        if (options == null) {
            return defaults;
        }
        Integer titleLevel = options.titleLevel() == null ? defaults.titleLevel() : options.titleLevel();
        Boolean stripHeaders = options.stripHeaders() == null ? defaults.stripHeaders() : options.stripHeaders();
        Boolean preserveCodeBlock = options.preserveCodeBlock() == null
                ? defaults.preserveCodeBlock()
                : options.preserveCodeBlock();
        Boolean createParentForOversized = options.createParentForOversized() == null
                ? defaults.createParentForOversized()
                : options.createParentForOversized();
        return new MarkdownSplitOptions(titleLevel, stripHeaders, preserveCodeBlock, createParentForOversized);
    }

    private RegexSplitOptions mergeRegexOptions(RegexSplitOptions options, RegexSplitOptions defaults) {
        if (options == null) {
            return defaults;
        }
        String separator = options.separator() == null ? defaults.separator() : options.separator();
        String regex = options.regex() == null ? defaults.regex() : options.regex();
        Boolean keepSeparator = options.keepSeparator() == null ? defaults.keepSeparator() : options.keepSeparator();
        return new RegexSplitOptions(separator, regex, keepSeparator);
    }

    private ExcelSplitOptions mergeExcelOptions(ExcelSplitOptions options, ExcelSplitOptions defaults) {
        if (options == null) {
            return defaults;
        }
        ExcelSplitMode mode = options.mode() == null ? defaults.mode() : options.mode();
        Boolean firstRowAsHeader = options.firstRowAsHeader() == null
                ? defaults.firstRowAsHeader()
                : options.firstRowAsHeader();
        String charset = options.charset() == null ? defaults.charset() : options.charset();
        Integer maxRowsPerChunk = options.maxRowsPerChunk() == null
                ? defaults.maxRowsPerChunk()
                : options.maxRowsPerChunk();
        return new ExcelSplitOptions(mode, firstRowAsHeader, charset, maxRowsPerChunk);
    }

    private MarkdownSplitOptions defaultMarkdownOptions() {
        return new MarkdownSplitOptions(DEFAULT_MARKDOWN_TITLE_LEVEL, false, true, true);
    }

    private RegexSplitOptions defaultRegexOptions() {
        return new RegexSplitOptions(DEFAULT_TEXT_SEPARATOR, null, false);
    }

    private ExcelSplitOptions defaultExcelOptions() {
        return new ExcelSplitOptions(ExcelSplitMode.KEY_VALUE, true, null, null);
    }

    private ParseConfigRequest mergeParseConfig(FileType fileType, ParseConfigRequest parseConfig) {
        ParseConfigRequest defaultConfig = defaultParseConfig(fileType);
        if (parseConfig == null) {
            return defaultConfig;
        }
        if (parseConfig.enableOcr() != null && parseConfig.enableImageDescription() != null) {
            return parseConfig;
        }
        Boolean enableOcr = parseConfig.enableOcr() == null ? defaultConfig.enableOcr() : parseConfig.enableOcr();
        Boolean enableImageDescription = parseConfig.enableImageDescription() == null
                ? defaultConfig.enableImageDescription()
                : parseConfig.enableImageDescription();
        return new ParseConfigRequest(enableOcr, enableImageDescription);
    }

    private ParseConfigRequest defaultParseConfig(FileType fileType) {
        return switch (fileType) {
            case PDF, WORD -> new ParseConfigRequest(true, false);
            case EXCEL, PPT, MARKDOWN, TEXT, UNKNOWN -> new ParseConfigRequest(false, false);
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
