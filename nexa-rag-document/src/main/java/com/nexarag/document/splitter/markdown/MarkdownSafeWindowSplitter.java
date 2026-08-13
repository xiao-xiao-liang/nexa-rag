package com.nexarag.document.splitter.markdown;

import com.nexarag.document.splitter.support.TextWindowSplitter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 面向 Markdown 结构的安全窗口切分器。
 *
 * <p>普通正文仍可按字符窗口切分；HTML 表格、围栏代码块和标题行则作为不可截断块处理。
 * 对超长 HTML 表格，仅允许在完整的 {@code </tr>} 行边界处拆分，并为每个片段补全 table 标签。</p>
 */
@Component
@RequiredArgsConstructor
public class MarkdownSafeWindowSplitter {

    private static final Pattern FENCE_OPENING_PATTERN =
            Pattern.compile("(?m)^[\\t ]*([`~]{3,})[^\\r\\n]*$");
    private static final Pattern HEADING_PATTERN = Pattern.compile("^[\\t ]{0,3}#{1,6}\\s+.+$");
    private static final Pattern TABLE_ROW_PATTERN = Pattern.compile("(?is)<tr\\b[^>]*>.*?</tr\\s*>");

    private final TextWindowSplitter textWindowSplitter;

    /**
     * 按安全块生成 Markdown 窗口。
     *
     * @param markdown  Markdown 内容
     * @param chunkSize 窗口字符上限
     * @param overlap   普通文本窗口重叠字符数
     * @return 不包含空白项的 Markdown 窗口
     */
    public List<String> split(String markdown, int chunkSize, int overlap) {
        // 复用通用窗口切分器的参数校验，确保两条切分路径的配置语义一致。
        textWindowSplitter.split("", chunkSize, overlap);
        if (!StringUtils.hasText(markdown)) {
            return List.of();
        }

        List<MarkdownBlock> blocks = parseBlocks(markdown);
        List<String> windows = new ArrayList<>();
        List<MarkdownBlock> currentBlocks = new ArrayList<>();

        for (MarkdownBlock block : blocks) {
            if (block.content().length() > chunkSize) {
                flushCurrent(windows, currentBlocks);
                appendOversizedBlock(windows, block, chunkSize, overlap);
                continue;
            }

            if (!currentBlocks.isEmpty() && joinedLength(currentBlocks, block) > chunkSize) {
                List<MarkdownBlock> overlapBlocks = trailingPlainTextOverlap(currentBlocks, overlap);
                flushCurrent(windows, currentBlocks);
                currentBlocks.addAll(overlapBlocks);
                if (!currentBlocks.isEmpty() && joinedLength(currentBlocks, block) > chunkSize) {
                    // 重叠文本不能单独形成一个无新信息的小窗口，容纳不下时直接放弃该次重叠。
                    currentBlocks.clear();
                }
            }
            currentBlocks.add(block);
        }
        flushCurrent(windows, currentBlocks);
        return windows;
    }

    private void appendOversizedBlock(List<String> windows, MarkdownBlock block, int chunkSize, int overlap) {
        if (block.type() == MarkdownBlockType.HTML_TABLE) {
            windows.addAll(splitTableByRows(block.content(), chunkSize));
            return;
        }
        if (block.type() == MarkdownBlockType.FENCED_CODE) {
            windows.add(block.content());
            return;
        }
        windows.addAll(textWindowSplitter.split(block.content(), chunkSize, overlap));
    }

    private void flushCurrent(List<String> windows, List<MarkdownBlock> currentBlocks) {
        if (currentBlocks.isEmpty()) {
            return;
        }
        String window = currentBlocks.stream()
                .map(MarkdownBlock::content)
                .filter(StringUtils::hasText)
                .reduce((left, right) -> left + "\n\n" + right)
                .orElse("");
        if (StringUtils.hasText(window)) {
            windows.add(window);
        }
        currentBlocks.clear();
    }

    private int joinedLength(List<MarkdownBlock> currentBlocks, MarkdownBlock nextBlock) {
        int length = nextBlock.content().length();
        for (MarkdownBlock currentBlock : currentBlocks) {
            length += currentBlock.content().length() + 2;
        }
        return length;
    }

    private List<MarkdownBlock> trailingPlainTextOverlap(List<MarkdownBlock> currentBlocks, int overlap) {
        if (overlap <= 0 || currentBlocks.isEmpty()) {
            return List.of();
        }
        MarkdownBlock lastBlock = currentBlocks.getLast();
        if (lastBlock.type() != MarkdownBlockType.PLAIN_TEXT) {
            return List.of();
        }
        String content = lastBlock.content();
        int startOffset = Math.max(0, content.length() - overlap);
        return List.of(new MarkdownBlock(content.substring(startOffset), MarkdownBlockType.PLAIN_TEXT));
    }

    private List<MarkdownBlock> parseBlocks(String markdown) {
        List<MarkdownBlock> blocks = new ArrayList<>();
        int cursor = 0;
        while (cursor < markdown.length()) {
            ProtectedRange protectedRange = findNextProtectedRange(markdown, cursor);
            if (protectedRange == null) {
                appendNormalBlocks(blocks, markdown.substring(cursor));
                break;
            }
            appendNormalBlocks(blocks, markdown.substring(cursor, protectedRange.startOffset()));
            blocks.add(new MarkdownBlock(markdown.substring(protectedRange.startOffset(), protectedRange.endOffset()),
                    protectedRange.type()));
            cursor = protectedRange.endOffset();
        }
        return blocks;
    }

    private ProtectedRange findNextProtectedRange(String markdown, int fromIndex) {
        ProtectedRange table = findNextTable(markdown, fromIndex);
        ProtectedRange fence = findNextFencedCodeBlock(markdown, fromIndex);
        if (table == null) {
            return fence;
        }
        if (fence == null) {
            return table;
        }
        return table.startOffset() < fence.startOffset() ? table : fence;
    }

    private ProtectedRange findNextTable(String markdown, int fromIndex) {
        String lowerCaseMarkdown = markdown.toLowerCase();
        int start = lowerCaseMarkdown.indexOf("<table", fromIndex);
        while (start >= 0) {
            int openingEnd = lowerCaseMarkdown.indexOf('>', start);
            if (openingEnd < 0) {
                return null;
            }
            int closingStart = lowerCaseMarkdown.indexOf("</table", openingEnd + 1);
            if (closingStart >= 0) {
                int closingEnd = lowerCaseMarkdown.indexOf('>', closingStart);
                if (closingEnd >= 0) {
                    return new ProtectedRange(start, closingEnd + 1, MarkdownBlockType.HTML_TABLE);
                }
            }
            start = lowerCaseMarkdown.indexOf("<table", openingEnd + 1);
        }
        return null;
    }

    private ProtectedRange findNextFencedCodeBlock(String markdown, int fromIndex) {
        Matcher openingMatcher = FENCE_OPENING_PATTERN.matcher(markdown);
        if (!openingMatcher.find(fromIndex)) {
            return null;
        }
        String openingFence = openingMatcher.group(1);
        char marker = openingFence.charAt(0);
        Pattern closingPattern = Pattern.compile("(?m)^[\\t ]*" + Pattern.quote(String.valueOf(marker))
                + "{" + openingFence.length() + ",}[\\t ]*$");
        Matcher closingMatcher = closingPattern.matcher(markdown);
        if (!closingMatcher.find(openingMatcher.end())) {
            return null;
        }
        return new ProtectedRange(openingMatcher.start(), closingMatcher.end(), MarkdownBlockType.FENCED_CODE);
    }

    private void appendNormalBlocks(List<MarkdownBlock> blocks, String normalText) {
        if (!StringUtils.hasText(normalText)) {
            return;
        }
        StringBuilder paragraph = new StringBuilder();
        for (String line : normalText.split("\\R", -1)) {
            if (HEADING_PATTERN.matcher(line).matches()) {
                appendParagraph(blocks, paragraph);
                blocks.add(new MarkdownBlock(line, MarkdownBlockType.HEADING));
                continue;
            }
            if (line.isBlank()) {
                appendParagraph(blocks, paragraph);
                continue;
            }
            if (!paragraph.isEmpty()) {
                paragraph.append('\n');
            }
            paragraph.append(line);
        }
        appendParagraph(blocks, paragraph);
    }

    private void appendParagraph(List<MarkdownBlock> blocks, StringBuilder paragraph) {
        if (StringUtils.hasText(paragraph.toString())) {
            blocks.add(new MarkdownBlock(paragraph.toString(), MarkdownBlockType.PLAIN_TEXT));
        }
        paragraph.setLength(0);
    }

    private List<String> splitTableByRows(String table, int chunkSize) {
        int openingEnd = table.indexOf('>') + 1;
        int closingStart = table.toLowerCase().lastIndexOf("</table");
        if (openingEnd <= 0 || closingStart <= openingEnd) {
            return List.of(table);
        }
        String openingTag = table.substring(0, openingEnd);
        String closingTag = table.substring(closingStart);
        String tableBody = table.substring(openingEnd, closingStart);
        List<String> rows = extractCompleteRows(tableBody);
        if (rows.size() < 2) {
            return List.of(table);
        }

        String headerRow = rows.getFirst();
        List<String> fragments = new ArrayList<>();
        StringBuilder current = new StringBuilder(openingTag).append(headerRow);
        for (int index = 1; index < rows.size(); index++) {
            String row = rows.get(index);
            if (current.length() > openingTag.length() + headerRow.length()
                    && current.length() + row.length() + closingTag.length() > chunkSize) {
                fragments.add(current.append(closingTag).toString());
                current = new StringBuilder(openingTag).append(headerRow);
            }
            current.append(row);
        }
        fragments.add(current.append(closingTag).toString());
        return fragments;
    }

    private List<String> extractCompleteRows(String tableBody) {
        Matcher matcher = TABLE_ROW_PATTERN.matcher(tableBody);
        List<String> rows = new ArrayList<>();
        int cursor = 0;
        while (matcher.find()) {
            if (!tableBody.substring(cursor, matcher.start()).isBlank()) {
                return List.of();
            }
            rows.add(matcher.group());
            cursor = matcher.end();
        }
        return tableBody.substring(cursor).isBlank() ? rows : List.of();
    }

    private record MarkdownBlock(String content, MarkdownBlockType type) {
    }

    private record ProtectedRange(int startOffset, int endOffset, MarkdownBlockType type) {
    }

    private enum MarkdownBlockType {
        PLAIN_TEXT,
        HEADING,
        HTML_TABLE,
        FENCED_CODE
    }
}
