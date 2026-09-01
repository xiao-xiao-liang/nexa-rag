package com.nexarag.document.model.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.nexarag.document.enums.DocumentPipelineMessageStatus;
import com.nexarag.document.enums.DocumentVersionStatus;
import com.nexarag.document.enums.FileType;
import com.nexarag.infra.enums.ExternalDocumentSourceType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 文档不可变版本数据对象，对应 document_version 表。
 */
@Getter
@Setter
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@TableName("document_version")
public class DocumentVersionDO {

    /** 文档版本ID。 */
    @TableId("document_version_id")
    private Long documentVersionId;

    /** 文档ID。 */
    private Long documentId;

    /** 文档内递增版本号。 */
    private Long revisionNo;

    /** 原始文件名。 */
    private String originalFileName;

    /** 文件类型。 */
    private FileType fileType;

    /** 文件大小。 */
    private Long fileSize;

    /** 原始文件地址。 */
    private String originalFileUrl;

    /** 原始文件对象名。 */
    private String originalObjectName;

    /** 文档来源类型。 */
    private ExternalDocumentSourceType sourceType;

    /** 外部来源URL。 */
    private String sourceUrl;

    /** 解析后文件地址。 */
    private String parsedFileUrl;

    /** 解析后文件对象名。 */
    private String parsedObjectName;

    /** 解析后内容类型。 */
    private String parsedContentType;

    /** 解析附属制品与结构元数据。 */
    private String parsedMetadataJson;

    /** 版本处理状态。 */
    private DocumentVersionStatus status;

    /** 当前版本处理轮次ID。 */
    private String processId;

    /** 文档流水线消息状态。 */
    private DocumentPipelineMessageStatus messageStatus;

    /** 消息消费次数。 */
    private Integer consumedTimes;

    /** 最近消费消息ID。 */
    private String lastMessageId;

    /** 排队阶段。 */
    private String queueStage;

    /** 排队时间。 */
    private LocalDateTime queueTime;

    /** 处理开始时间。 */
    private LocalDateTime processStartTime;

    /** 处理结束时间。 */
    private LocalDateTime processEndTime;

    /** 处理配置快照。 */
    private String processConfigJson;

    /** 失败阶段。 */
    private String failureStage;

    /** 失败原因。 */
    private String failureReason;

    /** 失败详情。 */
    private String failureDetail;

    /** 已重试次数。 */
    private Integer retryCount;

    /** 最大重试次数。 */
    private Integer maxRetryCount;

    /** 最近重试时间。 */
    private LocalDateTime lastRetryTime;

    /** 索引预热完成时间。 */
    private LocalDateTime indexReadyTime;

    /** 清理状态。 */
    private String cleanupStatus;

    /** 清理重试次数。 */
    private Integer cleanupRetryCount;

    /** 清理失败原因。 */
    private String cleanupFailureReason;

    /** 创建时间。 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /** 更新时间。 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    /** 创建人。 */
    private String createBy;

    /** 更新人。 */
    private String updateBy;

    /** 乐观锁版本号。 */
    @Version
    private Integer version;
}
