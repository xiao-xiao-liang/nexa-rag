package com.nexarag.document.toolkit.resolver;

import com.nexarag.document.model.bo.structure.DocumentStructureBO;
import com.nexarag.document.model.bo.structure.HeadingEvidenceBO;
import com.nexarag.document.model.bo.structure.ResolvedHeadingBO;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/** 将标题候选按原始顺序定位至 Markdown 行，避免重复标题回跳。 */
@Component
public class HeadingLineLocator {
    /** 返回已定位标题与未定位诊断。 */
    public DocumentStructureBO locate(String content, List<HeadingEvidenceBO> headings) {
        if (content == null || headings == null || headings.isEmpty()) {
            return DocumentStructureBO.empty();
        }
        String[] lines = content.replace("\r\n", "\n").replace('\r', '\n').split("\n", -1);
        List<ResolvedHeadingBO> resolved = new ArrayList<>();
        List<String> diagnostics = new ArrayList<>();
        int cursor = 0;
        for (HeadingEvidenceBO heading : headings) {
            int lineNumber = findLine(lines, heading.title(), cursor);
            if (lineNumber < 0) {
                diagnostics.add("标题未定位，sequence=" + heading.sequence());
                continue;
            }
            resolved.add(new ResolvedHeadingBO(heading.title(), heading.declaredLevel(), lineNumber,
                    heading.pageNumber(), heading.source(), heading.confidence()));
            cursor = lineNumber;
        }
        return new DocumentStructureBO(List.copyOf(resolved), List.copyOf(diagnostics));
    }

    private int findLine(String[] lines, String title, int cursor) {
        String normalizedTitle = normalize(title);
        for (int index = cursor; index < lines.length; index++) {
            if (normalize(lines[index]).equals(normalizedTitle)) {
                return index + 1;
            }
        }
        return -1;
    }

    /** 统一 Word 标题与 Pandoc Markdown 的行文本，保留原始标题用于下游展示。 */
    private String normalize(String text) {
        if (text == null) {
            return "";
        }
        String normalized = text.strip();
        while (normalized.startsWith("#")) {
            normalized = normalized.substring(1).stripLeading();
        }
        return normalized.replace("**", "").replace("__", "").replace("\\", "")
                .replaceAll("\\s+", " ").strip();
    }
}
