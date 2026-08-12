package com.nexarag.infra.parser.publish;

import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * Markdown 图片资源地址文件重写器，避免将整份 Markdown 加载到内存。
 */
@Component
public class MarkdownAssetFileRewriter {

    /**
     * 将 Markdown 中已映射图片的相对地址重写为对象存储访问地址。
     *
     * @param input     原始 Markdown 文件
     * @param output    重写后的 Markdown 文件
     * @param assetUrls 相对资源路径与访问地址映射
     * @throws IOException 文件读取或写入失败时抛出
     */
    public void rewrite(Path input, Path output, Map<String, String> assetUrls) throws IOException {
        // 1. 逐行读取和写入，避免正文规模影响堆内存
        try (BufferedReader reader = Files.newBufferedReader(input, StandardCharsets.UTF_8);
             BufferedWriter writer = Files.newBufferedWriter(output, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                writer.write(rewriteLine(line, assetUrls));
                writer.newLine();
            }
        }
    }

    private String rewriteLine(String line, Map<String, String> assetUrls) {
        if (line == null || line.isEmpty() || assetUrls == null || assetUrls.isEmpty()) {
            return line;
        }
        StringBuilder rewritten = new StringBuilder(line.length());
        int cursor = 0;
        while (cursor < line.length()) {
            int imageStart = line.indexOf("![", cursor);
            if (imageStart < 0) {
                rewritten.append(line, cursor, line.length());
                break;
            }
            int targetStart = line.indexOf("](", imageStart + 2);
            int targetEnd = targetStart < 0 ? -1 : line.indexOf(')', targetStart + 2);
            if (targetStart < 0 || targetEnd < 0) {
                rewritten.append(line, cursor, line.length());
                break;
            }
            rewritten.append(line, cursor, targetStart + 2);
            String originalTarget = line.substring(targetStart + 2, targetEnd);
            String replacementTarget = assetUrls.get(originalTarget);
            rewritten.append(replacementTarget == null ? originalTarget : replacementTarget);
            cursor = targetEnd;
        }
        return rewritten.toString();
    }
}
