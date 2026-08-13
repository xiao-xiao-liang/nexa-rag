package com.nexarag.document.toolkit.resolver;

import com.nexarag.document.enums.HeadingEvidenceSource;
import com.nexarag.document.model.bo.structure.HeadingEvidenceBO;
import com.nexarag.infra.config.DocumentStructureProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 标题证据融合器，以保守规则将多来源候选归一为可构造章节树的层级。
 */
@Component
public class HeadingHierarchyResolver {
    private final DocumentStructureProperties properties;

    public HeadingHierarchyResolver(DocumentStructureProperties properties) {
        this.properties = properties;
    }

    /** 融合候选标题，不访问外部存储。 */
    public List<HeadingEvidenceBO> resolve(List<HeadingEvidenceBO> evidences) {
        if (evidences == null || evidences.isEmpty()) {
            return List.of();
        }
        List<HeadingEvidenceBO> candidates = evidences.stream()
                .filter(this::isUsable)
                .sorted(Comparator.comparingInt(HeadingEvidenceBO::sequence)
                        .thenComparingInt(evidence -> priority(evidence.source())))
                .toList();
        List<HeadingEvidenceBO> resolved = new ArrayList<>();
        int previousLevel = 0;
        int previousSequence = Integer.MIN_VALUE;
        for (HeadingEvidenceBO candidate : candidates) {
            if (candidate.sequence() == previousSequence) {
                continue;
            }
            int level = Math.clamp(candidate.declaredLevel(), 1, 6);
            if (previousLevel > 0) {
                level = Math.min(level, previousLevel + 1);
            }
            resolved.add(new HeadingEvidenceBO(candidate.title(), level, candidate.sequence(), candidate.source(),
                    candidate.confidence(), candidate.pageNumber()));
            previousLevel = level;
            previousSequence = candidate.sequence();
        }
        return List.copyOf(resolved);
    }

    private boolean isUsable(HeadingEvidenceBO evidence) {
        return evidence != null && StringUtils.hasText(evidence.title()) && evidence.declaredLevel() > 0
                && (evidence.source() != HeadingEvidenceSource.HEURISTIC
                || (properties.isHeuristicHeadingEnabled()
                && evidence.confidence() >= properties.getHeuristicHeadingMinConfidence()));
    }

    private int priority(HeadingEvidenceSource source) {
        return switch (source) {
            case PDF_OUTLINE -> 0;
            case PDF_LAYOUT -> 1;
            case PDF_NUMBERING -> 2;
            case MARKDOWN -> 3;
            case WORD_OUTLINE -> 4;
            case WORD_STYLE -> 5;
            case NUMBERING -> 6;
            case HEURISTIC -> 7;
            case MINERU -> 8;
            case LLM -> 9;
        };
    }
}
