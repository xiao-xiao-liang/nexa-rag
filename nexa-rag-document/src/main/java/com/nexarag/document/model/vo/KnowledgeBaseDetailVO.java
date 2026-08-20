package com.nexarag.document.model.vo;

import java.time.LocalDateTime;

/**
 * 知识库详情展示对象。
 *
 * @param knowledgeBaseId 知识库ID
 * @param name 知识库名称
 * @param description 知识库描述
 * @param isDefault 是否默认知识库
 * @param statistics 文档状态统计
 * @param createTime 创建时间
 * @param updatedTime 更新时间
 */
public record KnowledgeBaseDetailVO(Long knowledgeBaseId, String name, String description, Integer isDefault,
                                    KnowledgeBaseStatisticsVO statistics, LocalDateTime createTime,
                                    LocalDateTime updatedTime) {
}
