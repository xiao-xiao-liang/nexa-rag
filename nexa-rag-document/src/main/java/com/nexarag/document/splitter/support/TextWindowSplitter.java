package com.nexarag.document.splitter.support;

import com.nexarag.common.exception.ServiceException;
import com.nexarag.document.error.DocumentErrorCode;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * 文本窗口切分工具，按字符长度和重叠量生成片段。
 */
@Component
public class TextWindowSplitter {

    /**
     * 切分文本。
     *
     * @param text       文本内容
     * @param chunkSize  片段大小
     * @param overlap    重叠大小
     * @return 文本片段列表
     */
    public List<String> split(String text, int chunkSize, int overlap) {
        return splitRanges(text, chunkSize, overlap).stream()
                .map(range -> text.substring(range.startOffset(), range.endOffset()))
                .toList();
    }

    /**
     * 切分文本并返回每个非空窗口在原文中的偏移范围。
     *
     * @param text      文本内容
     * @param chunkSize 片段大小
     * @param overlap   片段重叠大小
     * @return 文本窗口范围列表
     */
    public List<TextWindowRange> splitRanges(String text, int chunkSize, int overlap) {
        validateConfig(chunkSize, overlap);
        if (!StringUtils.hasText(text)) {
            return List.of();
        }

        // 1. 按窗口切分，并尽量在自然边界处截断
        List<TextWindowRange> ranges = new ArrayList<>();
        int start = 0;
        while (start < text.length()) {
            int end = chooseEnd(text, start, chunkSize);
            TextWindowRange range = trimRange(text, start, end);
            if (range != null) {
                ranges.add(range);
            }
            if (end >= text.length()) {
                break;
            }
            start = Math.max(start + 1, end - overlap);
        }
        return ranges;
    }

    private TextWindowRange trimRange(String text, int start, int end) {
        int trimmedStart = start;
        while (trimmedStart < end && text.charAt(trimmedStart) <= ' ') {
            trimmedStart++;
        }
        int trimmedEnd = end;
        while (trimmedEnd > trimmedStart && text.charAt(trimmedEnd - 1) <= ' ') {
            trimmedEnd--;
        }
        return trimmedStart == trimmedEnd ? null : new TextWindowRange(trimmedStart, trimmedEnd);
    }

    private void validateConfig(int chunkSize, int overlap) {
        if (chunkSize <= 0 || overlap < 0 || overlap >= chunkSize) {
            throw new ServiceException("切分配置不合法，chunkSize=" + chunkSize + "，overlap=" + overlap,
                    DocumentErrorCode.DOCUMENT_PROCESS_CONFIG_INVALID);
        }
    }

    private int chooseEnd(String text, int start, int chunkSize) {
        int maxEnd = Math.min(text.length(), start + chunkSize);
        if (maxEnd == text.length()) {
            return maxEnd;
        }

        // 1. 优先在段落边界截断
        int paragraphEnd = text.lastIndexOf("\n\n", maxEnd);
        if (paragraphEnd > start + chunkSize / 2) {
            return paragraphEnd + 2;
        }

        // 2. 其次在换行边界截断
        int lineEnd = text.lastIndexOf('\n', maxEnd);
        if (lineEnd > start + chunkSize / 2) {
            return lineEnd + 1;
        }

        return maxEnd;
    }
}
