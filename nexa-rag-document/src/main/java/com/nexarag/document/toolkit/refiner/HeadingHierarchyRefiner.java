package com.nexarag.document.toolkit.refiner;

import com.nexarag.document.model.bo.structure.HeadingEvidenceBO;

import java.util.List;

/**
 * 标题层级精修扩展点。
 *
 * <p>实现方只能调整既有标题候选的层级和置信度，不能新增、删除或重排标题，
 * 以保证文档结构恢复结果始终可追溯。</p>
 */
@FunctionalInterface
public interface HeadingHierarchyRefiner {

    /**
     * 精修已融合的标题层级。
     *
     * @param documentId 文档标识
     * @param headings 已融合的标题证据
     * @return 精修后的标题证据；无法精修时应原样返回
     */
    List<HeadingEvidenceBO> refine(Long documentId, List<HeadingEvidenceBO> headings);
}
