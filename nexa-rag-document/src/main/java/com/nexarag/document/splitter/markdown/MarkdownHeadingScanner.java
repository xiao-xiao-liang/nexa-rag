package com.nexarag.document.splitter.markdown;

import com.nexarag.document.model.dto.MarkdownSplitOptions;
import com.nexarag.document.splitter.DocumentSectionIdGenerator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * Markdown 标题扫描器，负责按标题层级生成文档区块。
 */
@Component
@RequiredArgsConstructor
public class MarkdownHeadingScanner {

    private final DocumentSectionIdGenerator sectionIdGenerator;

    /**
     * 扫描 Markdown 文本。
     *
     * @param content Markdown 内容
     * @param options Markdown 切分参数
     * @return Markdown 区块列表
     */
    public List<MarkdownSection> scan(String content, MarkdownSplitOptions options) {
        return scan(content, options, null);
    }

    /**
     * 扫描 Markdown 文本并构建标题树。
     *
     * @param content    Markdown 内容
     * @param options    Markdown 切分参数
     * @param documentId 文档ID
     * @return Markdown 章节列表
     * @throws MarkdownStructureException 标题层级无法构成可信树时抛出
     */
    public List<MarkdownSection> scan(String content, MarkdownSplitOptions options, Long documentId) {
        if (!StringUtils.hasText(content)) {
            throw new MarkdownStructureException("Markdown内容为空");
        }
        int titleLevel = options == null || options.titleLevel() == null ? 3 : options.titleLevel();
        boolean preserveCodeBlock = options == null || !Boolean.FALSE.equals(options.preserveCodeBlock());
        String[] lines = content.replace("\r\n", "\n").replace('\r', '\n').split("\n", -1);

        List<Heading> headings = new ArrayList<>();
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
                continue;
            }

            Heading heading = inCodeBlock ? null : parseHeading(line, titleLevel);
            if (heading != null) {
                headings.add(new Heading(heading.level(), heading.title(), i + 1));
            }
        }
        validateHeadings(headings, lines);
        return buildSections(headings, lines, documentId);
    }

    private void validateHeadings(List<Heading> headings, String[] lines) {
        if (headings.isEmpty()) {
            throw new MarkdownStructureException("Markdown内容不存在有效标题");
        }
        if (hasTextBeforeFirstHeading(headings.getFirst(), lines)) {
            throw new MarkdownStructureException("Markdown标题前存在未归属正文");
        }
        for (int i = 0; i < headings.size(); i++) {
            Heading current = headings.get(i);
            if (!StringUtils.hasText(current.title())) {
                throw new MarkdownStructureException("Markdown存在空标题，line=" + current.lineNumber());
            }
            if (i > 0 && current.level() > headings.get(i - 1).level() + 1) {
                throw new MarkdownStructureException("Markdown标题层级跳跃，line=" + current.lineNumber());
            }
        }
    }

    private boolean hasTextBeforeFirstHeading(Heading firstHeading, String[] lines) {
        for (int i = 0; i < firstHeading.lineNumber() - 1; i++) {
            if (StringUtils.hasText(lines[i])) {
                return true;
            }
        }
        return false;
    }

    private List<MarkdownSection> buildSections(List<Heading> headings, String[] lines, Long documentId) {
        List<MarkdownSection> sections = new ArrayList<>();
        List<SectionStackEntry> stack = new ArrayList<>();
        for (int i = 0; i < headings.size(); i++) {
            Heading heading = headings.get(i);
            while (!stack.isEmpty() && stack.getLast().level() >= heading.level()) {
                stack.removeLast();
            }
            Long parentSectionId = stack.isEmpty() ? null : stack.getLast().sectionId();
            List<String> headingPath = new ArrayList<>();
            if (!stack.isEmpty()) {
                headingPath.addAll(stack.getLast().headingPath());
            }
            headingPath.add(heading.title());
            Long sectionId = sectionIdGenerator.nextSectionId(documentId);
            int bodyEndLine = i + 1 < headings.size() ? headings.get(i + 1).lineNumber() - 1 : lines.length;
            int endLine = findSectionEndLine(headings, i, lines.length);
            String bodyText = joinLines(lines, heading.lineNumber() + 1, bodyEndLine);
            sections.add(new MarkdownSection(sectionId, parentSectionId, heading.level(), heading.title(), headingPath,
                    heading.lineNumber(), endLine, heading.lineNumber() + 1, bodyEndLine, bodyText));
            stack.add(new SectionStackEntry(sectionId, heading.level(), headingPath));
        }
        return sections;
    }

    private int findSectionEndLine(List<Heading> headings, int headingIndex, int documentEndLine) {
        int currentLevel = headings.get(headingIndex).level();
        for (int i = headingIndex + 1; i < headings.size(); i++) {
            if (headings.get(i).level() <= currentLevel) {
                return headings.get(i).lineNumber() - 1;
            }
        }
        return documentEndLine;
    }

    private String joinLines(String[] lines, int startLine, int endLine) {
        if (startLine > endLine) {
            return "";
        }
        List<String> bodyLines = new ArrayList<>();
        for (int line = startLine; line <= endLine; line++) {
            bodyLines.add(lines[line - 1]);
        }
        return String.join("\n", bodyLines).trim();
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
        return new Heading(level, trimmed.substring(level).trim(), 0);
    }

    private record Heading(int level, String title, int lineNumber) {
    }

    private record SectionStackEntry(Long sectionId, int level, List<String> headingPath) {
    }
}
