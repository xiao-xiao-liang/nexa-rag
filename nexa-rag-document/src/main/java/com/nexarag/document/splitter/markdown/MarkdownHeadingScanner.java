package com.nexarag.document.splitter.markdown;

import com.nexarag.common.exception.ServiceException;
import com.nexarag.document.enums.DocumentErrorCode;
import com.nexarag.document.model.bo.split.MarkdownSection;
import com.nexarag.document.model.dto.MarkdownSplitOptions;
import com.nexarag.document.model.bo.structure.DocumentStructureBO;
import com.nexarag.document.model.bo.structure.ResolvedHeadingBO;
import com.nexarag.document.toolkit.DocumentSectionIdGenerator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
     * @throws ServiceException 标题层级无法构成可信树时抛出
     */
    public List<MarkdownSection> scan(String content, MarkdownSplitOptions options, Long documentId) {
        if (!StringUtils.hasText(content)) {
            throw markdownStructureException("Markdown内容为空");
        }
        int titleLevel = options == null || options.titleLevel() == null ? 6 : options.titleLevel();
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
        normalizeHeadings(headings);
        return buildSections(headings, lines, documentId);
    }

    /**
     * 按外部恢复的标题结构扫描 Markdown；无可用结构时保留 Markdown 原生扫描。
     */
    public List<MarkdownSection> scan(String content, MarkdownSplitOptions options, Long documentId,
                                      DocumentStructureBO structure) {
        if (structure == null || structure.headings().isEmpty()) {
            return scan(content, options, documentId);
        }
        String[] lines = content.replace("\r\n", "\n").replace('\r', '\n').split("\n", -1);
        int titleLevel = options == null || options.titleLevel() == null ? 6 : options.titleLevel();
        List<ResolvedHeadingBO> resolvedHeadings = structure.headings().stream()
                .filter(heading -> heading.lineNumber() > 0 && heading.lineNumber() <= lines.length)
                .toList();
        List<Heading> headings = resolvedHeadings.stream()
                .filter(heading -> heading.level() <= titleLevel)
                .map(heading -> new Heading(heading.level(), heading.title(), heading.lineNumber()))
                .toList();
        if (headings.isEmpty()) {
            return scan(content, options, documentId);
        }
        List<Heading> normalized = new ArrayList<>(headings);
        normalizeHeadings(normalized);
        return buildSections(normalized, rewriteResolvedHeadingLevels(lines, resolvedHeadings), documentId);
    }

    /**
     * 同步已恢复标题在片段正文中的 Markdown 层级，避免未切分的深层标题仍使用 MinerU 原始层级。
     */
    private String[] rewriteResolvedHeadingLevels(String[] lines, List<ResolvedHeadingBO> resolvedHeadings) {
        Map<Integer, Integer> levelByLine = new HashMap<>();
        for (ResolvedHeadingBO heading : resolvedHeadings) {
            levelByLine.putIfAbsent(heading.lineNumber(), heading.level());
        }
        String[] rewrittenLines = lines.clone();
        levelByLine.forEach((lineNumber, level) -> rewrittenLines[lineNumber - 1]
                = rewriteAtxHeadingLevel(rewrittenLines[lineNumber - 1], level));
        return rewrittenLines;
    }

    /**
     * 仅重写合法 ATX 标题的井号前缀，保留原有标题正文、缩进和内联格式。
     */
    private String rewriteAtxHeadingLevel(String line, int level) {
        String leadingTrimmed = line.stripLeading();
        int originalLevel = 0;
        while (originalLevel < leadingTrimmed.length() && leadingTrimmed.charAt(originalLevel) == '#') {
            originalLevel++;
        }
        if (originalLevel == 0 || originalLevel > 6 || leadingTrimmed.length() <= originalLevel
                || leadingTrimmed.charAt(originalLevel) != ' ') {
            return line;
        }
        int indentationLength = line.length() - leadingTrimmed.length();
        return line.substring(0, indentationLength) + "#".repeat(Math.clamp(level, 1, 6))
                + leadingTrimmed.substring(originalLevel);
    }

    private void normalizeHeadings(List<Heading> headings) {
        if (headings.isEmpty()) {
            throw markdownStructureException("Markdown内容不存在有效标题");
        }
        for (int i = 0; i < headings.size(); i++) {
            Heading current = headings.get(i);
            if (!StringUtils.hasText(current.title())) {
                throw markdownStructureException("Markdown存在空标题，line=" + current.lineNumber());
            }
            if (i > 0 && current.level() > headings.get(i - 1).level() + 1) {
                headings.set(i, new Heading(headings.get(i - 1).level() + 1, current.title(), current.lineNumber()));
            }
        }
    }

    private ServiceException markdownStructureException(String message) {
        return new ServiceException(message, DocumentErrorCode.DOCUMENT_MARKDOWN_STRUCTURE_INVALID);
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
