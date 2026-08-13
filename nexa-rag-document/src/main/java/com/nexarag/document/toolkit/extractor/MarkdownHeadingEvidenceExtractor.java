package com.nexarag.document.toolkit.extractor;

import com.nexarag.document.enums.HeadingEvidenceSource;
import com.nexarag.document.model.bo.structure.HeadingEvidenceBO;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/** 从 Markdown ATX 标题中提取最高可信度的结构证据。 */
@Component
public class MarkdownHeadingEvidenceExtractor {
    /** 忽略代码围栏内的标题符号并提取 1 至 6 级标题。 */
    public List<HeadingEvidenceBO> extract(String content) {
        if (!StringUtils.hasText(content)) {
            return List.of();
        }
        List<HeadingEvidenceBO> evidences = new ArrayList<>();
        boolean inCodeBlock = false;
        String[] lines = content.replace("\r\n", "\n").replace('\r', '\n').split("\n", -1);
        for (int index = 0; index < lines.length; index++) {
            String trimmed = lines[index].stripLeading();
            if (trimmed.startsWith("```") || trimmed.startsWith("~~~")) {
                inCodeBlock = !inCodeBlock;
                continue;
            }
            if (inCodeBlock) {
                continue;
            }
            int level = 0;
            while (level < trimmed.length() && trimmed.charAt(level) == '#') {
                level++;
            }
            if (level > 0 && level <= 6 && trimmed.length() > level && trimmed.charAt(level) == ' ') {
                String title = trimmed.substring(level).trim();
                if (StringUtils.hasText(title)) {
                    evidences.add(new HeadingEvidenceBO(title, level, index + 1, HeadingEvidenceSource.MARKDOWN,
                            1.0D, null));
                }
            }
        }
        return List.copyOf(evidences);
    }
}
