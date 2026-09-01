package com.nexarag.document.model.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.nexarag.document.enums.DocumentTaskStatus;
import com.nexarag.document.enums.DocumentTaskType;
import com.nexarag.document.enums.OutboxPublishStatus;
import lombok.*;

import java.time.LocalDateTime;

/**
 * 文档流水线Outbox实体，用于保存待发布的文档处理消息。
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("document_task_outbox")
public class DocumentTaskOutboxDO {

    /**
     * Outbox记录ID。
     */
    @TableId("outbox_id")
    private Long outboxId;

    /**
     * 文档ID。
     */
    private Long documentId;

    /**
     * 文档版本ID；文档处理及版本清理任务必须携带该字段。
     */
    private Long documentVersionId;

    /**
     * 父任务Outbox ID，仅告警任务使用。
     */
    private Long parentOutboxId;

    /**
     * 任务操作版本ID；处理任务使用处理流水号。
     */
    @TableField("operation_id")
    private String processId;

    /**
     * 生效代次；仅版本切换投影任务使用。
     */
    private Long activationGeneration;

    /**
     * 任务类型。
     */
    private DocumentTaskType taskType;

    /**
     * 消息唯一键。
     */
    private String messageKey;

    /**
     * 消息主题。
     */
    private String topic;

    /**
     * 消息内容。
     */
    private String messageBody;

    /**
     * 发布状态。
     */
    private OutboxPublishStatus publishStatus;

    /**
     * 消费者最终执行状态。
     */
    private DocumentTaskStatus taskStatus;

    /**
     * 发布重试次数。
     */
    private Integer publishRetryCount;

    /**
     * 消费者执行重试次数。
     */
    private Integer consumeRetryCount;

    /**
     * 下次重试时间。
     */
    private LocalDateTime nextRetryTime;

    /**
     * 锁持有者。
     */
    private String lockOwner;

    /**
     * 加锁时间。
     */
    private LocalDateTime lockTime;

    /**
     * 发布时间。
     */
    private LocalDateTime publishedTime;

    /**
     * 任务最终完成时间。
     */
    private LocalDateTime taskCompletedTime;

    /**
     * 消息发布失败原因。
     */
    @TableField("publish_failure_reason")
    private String failureReason;

    /**
     * 消费者最终失败原因。
     */
    private String taskFailureReason;

    /**
     * 创建时间。
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /**
     * 更新时间。
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
