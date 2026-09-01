package com.nexarag.document.model.vo;

import com.nexarag.document.enums.DocumentVersionOperationType;

import java.time.LocalDateTime;

/**
 * 文档版本操作审计展示对象。
 *
 * @param operationLogId 审计记录ID
 * @param documentVersionId 文档版本ID
 * @param operationType 操作类型
 * @param activationGeneration 生效代次
 * @param operatorId 操作者ID
 * @param operationDetail 操作详情
 * @param createTime 操作时间
 */
public record DocumentVersionOperationLogVO(
        Long operationLogId,
        Long documentVersionId,
        DocumentVersionOperationType operationType,
        Long activationGeneration,
        String operatorId,
        String operationDetail,
        LocalDateTime createTime) {
}
