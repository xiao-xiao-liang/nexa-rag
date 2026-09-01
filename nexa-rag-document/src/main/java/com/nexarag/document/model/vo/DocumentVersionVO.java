package com.nexarag.document.model.vo;

import com.nexarag.document.enums.DocumentVersionStatus;

import java.time.LocalDateTime;

/**
 * 文档版本展示对象。
 *
 * @param documentVersionId 文档版本ID
 * @param revisionNo 文档内版本号
 * @param active 是否为当前生效版本
 * @param originalFileName 原始文件名
 * @param status 版本状态
 * @param failureStage 失败阶段
 * @param failureReason 失败原因
 * @param indexReadyTime 索引预热完成时间
 * @param createTime 创建时间
 */
public record DocumentVersionVO(
        Long documentVersionId,
        Long revisionNo,
        boolean active,
        String originalFileName,
        DocumentVersionStatus status,
        String failureStage,
        String failureReason,
        LocalDateTime indexReadyTime,
        LocalDateTime createTime) {
}
