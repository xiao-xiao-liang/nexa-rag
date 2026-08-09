package com.nexarag.document.splitter.text;

import com.nexarag.common.exception.ServiceException;
import com.nexarag.document.model.dto.RegexSplitOptions;
import com.nexarag.document.model.dto.SplitConfigRequest;
import com.nexarag.document.enums.SplitStrategy;
import com.nexarag.document.enums.DocumentErrorCode;
import com.nexarag.document.splitter.ChunkDraft;
import com.nexarag.document.toolkit.DocumentChunkIdGenerator;
import com.nexarag.document.splitter.DocumentSplitContext;
import com.nexarag.document.splitter.DocumentSplitResult;
import com.nexarag.document.splitter.DocumentSplitter;
import com.nexarag.document.splitter.support.TextWindowSplitter;
import com.nexarag.document.toolkit.RegexSafetyValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 正则文本切分器，负责处理普通文本和 Tika 解析后的文本内容。
 */
@Component
@RequiredArgsConstructor
public class RegexTextDocumentSplitter implements DocumentSplitter {

    private final TextWindowSplitter textWindowSplitter;
    private final DocumentChunkIdGenerator chunkIdGenerator;
    private final RegexSafetyValidator regexSafetyValidator;

    @Override
    public SplitStrategy strategy() {
        return SplitStrategy.REGEX_TEXT;
    }

    /**
     * 按正则、分隔符或长度切分文本。
     *
     * @param context 文档切分上下文
     * @return 文档切分结果
     */
    @Override
    public DocumentSplitResult split(DocumentSplitContext context) {
        if (context == null || !StringUtils.hasText(context.content()) || context.config() == null) {
            throw new ServiceException("文本切分上下文不完整", DocumentErrorCode.DOCUMENT_PROCESS_CONFIG_INVALID);
        }
        SplitConfigRequest config = context.config();
        RegexSplitOptions options = config.regex();
        List<String> rawParts = splitRawParts(context.content(), options);
        List<String> chunks = mergeAndWindow(rawParts, config.chunkSize(), config.chunkOverlap());

        // 1. 转换为片段草稿
        List<ChunkDraft> drafts = new ArrayList<>();
        for (int i = 0; i < chunks.size(); i++) {
            drafts.add(new ChunkDraft(chunkIdGenerator.nextChunkId(context.documentId()), null, null, chunks.get(i),
                    null, null, metadata(context, options, i), false));
        }
        return DocumentSplitResult.unstructured(drafts);
    }

    private List<String> splitRawParts(String content, RegexSplitOptions options) {
        if (options != null && StringUtils.hasText(options.regex())) {
            return regexSafetyValidator.validateAndCompile(options.regex())
                    .splitAsStream(content)
                    .toList();
        }
        if (options != null && StringUtils.hasText(options.separator())) {
            return List.of(content.split(Pattern.quote(options.separator())));
        }
        return List.of(content);
    }

    private List<String> mergeAndWindow(List<String> rawParts, int chunkSize, int overlap) {
        List<String> chunks = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String rawPart : rawParts) {
            String part = rawPart == null ? "" : rawPart.trim();
            if (!StringUtils.hasText(part)) {
                continue;
            }
            if (part.length() > chunkSize) {
                flushCurrent(chunks, current);
                chunks.addAll(textWindowSplitter.split(part, chunkSize, overlap));
                continue;
            }
            if (!current.isEmpty() && current.length() + part.length() + 2 > chunkSize) {
                flushCurrent(chunks, current);
            }
            if (!current.isEmpty()) {
                current.append("\n\n");
            }
            current.append(part);
        }
        flushCurrent(chunks, current);
        return chunks;
    }

    private void flushCurrent(List<String> chunks, StringBuilder current) {
        String text = current.toString().trim();
        if (StringUtils.hasText(text)) {
            chunks.add(text);
        }
        current.setLength(0);
    }

    private Map<String, Object> metadata(DocumentSplitContext context, RegexSplitOptions options, int partIndex) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("splitStrategy", strategy().name());
        metadata.put("fileType", context.fileType().name());
        metadata.put("separator", options == null ? null : options.separator());
        metadata.put("regex", options == null ? null : options.regex());
        metadata.put("partIndex", partIndex);
        return metadata;
    }
}
