package com.nexarag.document.model.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.nexarag.infra.enums.ExternalDocumentSourceType;
import com.nexarag.document.enums.DocumentPipelineMessageStatus;
import com.nexarag.document.enums.DocumentStatus;
import com.nexarag.document.enums.FileType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 文档实体，对应 document 表，保存用户上传文档及处理状态。
 */
@Getter
@Setter
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@TableName("document")
public class Document {

    /**
     * 文档ID。
     */
    @TableId("document_id")
    private Long documentId;

    /**
     * 文档标题。
     */
    private String title;

    /**
     * 文档描述。
     */
    private String description;

    /**
     * 原始文件名。
     */
    private String originalFileName;

    /**
     * 文件类型。
     */
    private FileType fileType;

    /**
     * 文件大小。
     */
    private Long fileSize;

    /**
     * 原始文件地址。
     */
    private String originalFileUrl;

    /**
     * 原始文件对象名。
     */
    private String originalObjectName;

    /** 文档来源类型。 */
    private ExternalDocumentSourceType sourceType;

    /** 外部来源URL。 */
    private String sourceUrl;

    /**
     * 解析后文件地址。
     */
    private String parsedFileUrl;

    /**
     * 解析后文件对象名。
     */
    private String parsedObjectName;

    /**
     * 解析后内容类型。
     */
    private String parsedContentType;

    /**
     * 文档处理状态。
     */
    private DocumentStatus status;

    /**
     * 文档处理流水号。
     */
    private String processId;

    /**
     * 文档流水线消息状态。
     */
    private DocumentPipelineMessageStatus messageStatus;

    /**
     * 消息消费次数。
     */
    private Integer consumedTimes;

    /**
     * 最近消费消息ID。
     */
    private String lastMessageId;

    /**
     * 排队阶段。
     */
    private String queueStage;

    /**
     * 排队时间。
     */
    private LocalDateTime queueTime;

    /**
     * 处理开始时间。
     */
    private LocalDateTime processStartTime;

    /**
     * 处理结束时间。
     */
    private LocalDateTime processEndTime;

    /**
     * 处理配置快照。
     */
    private String processConfigJson;

    /**
     * 失败阶段。
     */
    private String failureStage;

    /**
     * 失败原因。
     */
    private String failureReason;

    /**
     * 失败详情。
     */
    private String failureDetail;

    /**
     * 已重试次数。
     */
    private Integer retryCount;

    /**
     * 最大重试次数。
     */
    private Integer maxRetryCount;

    /**
     * 最近重试时间。
     */
    private LocalDateTime lastRetryTime;

    /**
     * 清理状态。
     */
    private String cleanupStatus;

    /**
     * 清理重试次数。
     */
    private Integer cleanupRetryCount;

    /**
     * 清理失败原因。
     */
    private String cleanupFailureReason;

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

    /**
     * 创建人。
     */
    private String createBy;

    /**
     * 更新人。
     */
    private String updateBy;

    /**
     * 删除标记：0未删除，1已删除。
     */
    @TableLogic(value = "0", delval = "1")
    @TableField(fill = FieldFill.INSERT)
    private Integer delFlag;

    /**
     * 删除时间。
     */
    private LocalDateTime deleteTime;

    /**
     * 乐观锁版本号。
     */
    @Version
    private Integer version;
}
