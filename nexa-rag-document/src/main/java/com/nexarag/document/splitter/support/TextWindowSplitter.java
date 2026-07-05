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
        validateConfig(chunkSize, overlap);
        if (!StringUtils.hasText(text)) {
            return List.of();
        }

        // 1. 按窗口切分，并尽量在自然边界处截断
        List<String> chunks = new ArrayList<>();
        int start = 0;
        while (start < text.length()) {
            int end = chooseEnd(text, start, chunkSize);
            String chunk = text.substring(start, end).trim();
            if (StringUtils.hasText(chunk)) {
                chunks.add(chunk);
            }
            if (end >= text.length()) {
                break;
            }
            start = Math.max(0, end - overlap);
        }
        return chunks;
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
