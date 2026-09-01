package com.nexarag.document.model.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.nexarag.document.enums.DocumentVersionOperationType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 文档版本操作审计数据对象，对应永久保留的审计记录。
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("document_version_operation_log")
public class DocumentVersionOperationLogDO {

    /** 版本操作审计ID。 */
    @TableId("operation_log_id")
    private Long operationLogId;

    /** 文档ID。 */
    private Long documentId;

    /** 文档版本ID。 */
    private Long documentVersionId;

    /** 操作类型。 */
    private DocumentVersionOperationType operationType;

    /** 生效代次。 */
    private Long activationGeneration;

    /** 操作者ID。 */
    private String operatorId;

    /** 操作详情。 */
    private String operationDetail;

    /** 操作时间。 */
    private LocalDateTime createTime;
}
