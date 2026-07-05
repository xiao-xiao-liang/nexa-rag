package com.nexarag.document.splitter.markdown;

import com.nexarag.document.dto.MarkdownSplitOptions;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * Markdown 标题扫描器，负责按标题层级生成文档区块。
 */
@Component
public class MarkdownHeadingScanner {

    /**
     * 扫描 Markdown 文本。
     *
     * @param content Markdown 内容
     * @param options Markdown 切分参数
     * @return Markdown 区块列表
     */
    public List<MarkdownSection> scan(String content, MarkdownSplitOptions options) {
        if (!StringUtils.hasText(content)) {
            return List.of();
        }
        int titleLevel = options == null || options.titleLevel() == null ? 3 : options.titleLevel();
        boolean stripHeaders = options != null && Boolean.TRUE.equals(options.stripHeaders());
        boolean preserveCodeBlock = options == null || !Boolean.FALSE.equals(options.preserveCodeBlock());
        String[] lines = content.replace("\r\n", "\n").replace('\r', '\n').split("\n", -1);

        List<MarkdownSection> sections = new ArrayList<>();
        List<Heading> stack = new ArrayList<>();
        List<String> currentLines = new ArrayList<>();
        int currentStartLine = 1;
        int currentLevel = 0;
        String currentTitle = null;
        List<String> currentPath = List.of();
        boolean inCodeBlock = false;
        String fence = null;

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            String trimmed = line.trim();
            if (preserveCodeBlock && isFence(trimmed)) {
                if (!inCodeBlock) {
                    inCodeBlock = true;
                    fence = trimmed.substring(0, 3);
                } else if (trimmed.startsWith(fence)) {
                    inCodeBlock = false;
                    fence = null;
                }
                currentLines.add(line);
                continue;
            }

            Heading heading = inCodeBlock ? null : parseHeading(line, titleLevel);
            if (heading != null) {
                addSection(sections, currentLines, currentStartLine, i, currentLevel, currentTitle, currentPath);
                currentLines = new ArrayList<>();
                currentStartLine = i + 1;
                while (!stack.isEmpty() && stack.getLast().level() >= heading.level()) {
                    stack.removeLast();
                }
                stack.add(heading);
                currentLevel = heading.level();
                currentTitle = heading.title();
                currentPath = stack.stream().map(Heading::title).toList();
                if (!stripHeaders) {
                    currentLines.add(line);
                }
            } else {
                currentLines.add(line);
            }
        }
        addSection(sections, currentLines, currentStartLine, lines.length, currentLevel, currentTitle, currentPath);
        return sections;
    }

    private void addSection(List<MarkdownSection> sections,
                            List<String> lines,
                            int startLine,
                            int endLine,
                            int level,
                            String title,
                            List<String> titlePath) {
        String text = String.join("\n", lines).trim();
        if (StringUtils.hasText(text)) {
            sections.add(new MarkdownSection(level, title, titlePath, startLine, endLine, text));
        }
    }

    private boolean isFence(String trimmed) {
        return trimmed.startsWith("```") || trimmed.startsWith("~~~");
    }

    private Heading parseHeading(String line, int maxLevel) {
        String trimmed = line.stripLeading();
        int level = 0;
        while (level < trimmed.length() && trimmed.charAt(level) == '#') {
            level++;
        }
        if (level == 0 || level > 6 || level > maxLevel) {
            return null;
        }
        if (trimmed.length() > level && trimmed.charAt(level) != ' ') {
            return null;
        }
        return new Heading(level, trimmed.substring(level).trim());
    }

    private record Heading(int level, String title) {
    }
}
