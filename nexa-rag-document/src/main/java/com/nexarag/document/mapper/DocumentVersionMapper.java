package com.nexarag.document.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.nexarag.document.model.entity.DocumentVersionDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * 文档版本数据访问接口。
 */
@Mapper
public interface DocumentVersionMapper extends BaseMapper<DocumentVersionDO> {

    /**
     * 仅在当前处理轮次仍处于切分完成状态时标记为索引中。
     */
    @Update("""
            UPDATE document_version
            SET status = 'INDEXING', process_start_time = COALESCE(process_start_time, NOW()), update_time = NOW()
            WHERE document_id = #{documentId}
              AND document_version_id = #{documentVersionId}
              AND process_id = #{processId}
              AND status = 'CHUNKED'
            """)
    int markIndexing(@Param("documentId") Long documentId,
                     @Param("documentVersionId") Long documentVersionId,
                     @Param("processId") String processId);

    /**
     * 仅在当前处理轮次仍处于索引中状态时标记索引预热完成。
     */
    @Update("""
            UPDATE document_version
            SET status = 'INDEX_READY', index_ready_time = NOW(), process_end_time = NOW(),
                failure_stage = NULL, failure_reason = NULL, failure_detail = NULL, update_time = NOW()
            WHERE document_id = #{documentId}
              AND document_version_id = #{documentVersionId}
              AND process_id = #{processId}
              AND status = 'INDEXING'
            """)
    int markIndexReady(@Param("documentId") Long documentId,
                       @Param("documentVersionId") Long documentVersionId,
                       @Param("processId") String processId);

    /**
     * 记录指定版本、指定处理轮次的消息消费状态。
     *
     * @param documentId 文档ID
     * @param documentVersionId 文档版本ID
     * @param processId 处理轮次ID
     * @param messageId 消息ID
     * @param consumedTimes 当前消息累计消费次数
     * @return 受影响行数，1表示处理边界仍有效
     */
    @Update("""
            UPDATE document_version
            SET message_status = CASE WHEN #{consumedTimes} > 1 THEN 'RETRYING' ELSE 'PROCESSING' END,
                consumed_times = #{consumedTimes},
                retry_count = GREATEST(#{consumedTimes} - 1, 0),
                last_retry_time = CASE WHEN #{consumedTimes} > 1 THEN NOW() ELSE NULL END,
                last_message_id = #{messageId},
                update_time = NOW()
            WHERE document_id = #{documentId}
              AND document_version_id = #{documentVersionId}
              AND process_id = #{processId}
              AND status NOT IN ('INDEX_READY', 'FAILED')
            """)
    int recordMessageConsumption(@Param("documentId") Long documentId,
                                 @Param("documentVersionId") Long documentVersionId,
                                 @Param("processId") String processId,
                                 @Param("messageId") String messageId,
                                 @Param("consumedTimes") int consumedTimes);

    /**
     * 记录可重试异常，并保持处理边界指向当前版本和当前轮次。
     */
    @Update("""
            UPDATE document_version
            SET status = 'QUEUED', message_status = 'RETRYING',
                failure_stage = #{failureStage}, failure_reason = #{failureReason},
                failure_detail = #{failureDetail}, process_end_time = NULL,
                update_time = NOW()
            WHERE document_id = #{documentId}
              AND document_version_id = #{documentVersionId}
              AND process_id = #{processId}
              AND status NOT IN ('INDEX_READY', 'FAILED')
            """)
    int recordRetryableFailure(@Param("documentId") Long documentId,
                               @Param("documentVersionId") Long documentVersionId,
                               @Param("processId") String processId,
                               @Param("failureStage") String failureStage,
                               @Param("failureReason") String failureReason,
                               @Param("failureDetail") String failureDetail);

    /**
     * 标记指定版本的当前处理轮次消息已完成。
     */
    @Update("""
            UPDATE document_version
            SET message_status = 'COMPLETED', update_time = NOW()
            WHERE document_id = #{documentId}
              AND document_version_id = #{documentVersionId}
              AND process_id = #{processId}
              AND status = 'INDEX_READY'
            """)
    int markMessageCompleted(@Param("documentId") Long documentId,
                             @Param("documentVersionId") Long documentVersionId,
                             @Param("processId") String processId);

    /**
     * 将指定版本的当前处理轮次标记为最终失败。
     */
    @Update("""
            UPDATE document_version
            SET status = 'FAILED', message_status = 'FAILED',
                failure_stage = #{failureStage}, failure_reason = #{failureReason},
                failure_detail = #{failureDetail}, consumed_times = #{consumedTimes},
                retry_count = GREATEST(#{consumedTimes} - 1, 0),
                last_message_id = #{messageId},
                last_retry_time = CASE WHEN #{consumedTimes} > 1 THEN #{failureTime} ELSE NULL END,
                process_end_time = #{failureTime}, update_time = NOW()
            WHERE document_id = #{documentId}
              AND document_version_id = #{documentVersionId}
              AND process_id = #{processId}
              AND status NOT IN ('INDEX_READY', 'FAILED')
            """)
    int markProcessFailed(@Param("documentId") Long documentId,
                          @Param("documentVersionId") Long documentVersionId,
                          @Param("processId") String processId,
                          @Param("failureStage") String failureStage,
                          @Param("failureReason") String failureReason,
                          @Param("failureDetail") String failureDetail,
                          @Param("consumedTimes") int consumedTimes,
                          @Param("messageId") String messageId,
                          @Param("failureTime") java.time.LocalDateTime failureTime);

    /** 将最终失败版本以新的处理轮次重新入队。 */
    @Update("""
            UPDATE document_version
            SET status = 'QUEUED', process_id = #{processId}, message_status = 'PENDING_PUBLISH',
                consumed_times = 0, last_message_id = NULL, queue_stage = 'PIPELINE', queue_time = NOW(),
                process_start_time = NULL, process_end_time = NULL, failure_stage = NULL, failure_reason = NULL,
                failure_detail = NULL, retry_count = 0, last_retry_time = NULL, update_by = #{operator}, update_time = NOW()
            WHERE document_id = #{documentId} AND document_version_id = #{documentVersionId}
              AND status = 'FAILED'
            """)
    int retryFailedVersion(@Param("documentId") Long documentId,
                           @Param("documentVersionId") Long documentVersionId,
                           @Param("processId") String processId,
                           @Param("operator") String operator);

    /** 将非生效、非构建版本标记为永久删除中。 */
    @Update("""
            UPDATE document_version
            SET status = 'DELETING', cleanup_status = 'PENDING', cleanup_retry_count = 0,
                cleanup_failure_reason = NULL, update_by = #{operator}, update_time = NOW()
            WHERE document_id = #{documentId} AND document_version_id = #{documentVersionId}
              AND status IN ('INDEX_READY', 'FAILED')
            """)
    int markDeleting(@Param("documentId") Long documentId,
                     @Param("documentVersionId") Long documentVersionId,
                     @Param("operator") String operator);

    /** 将逻辑删除文档下的单个版本标记为删除中，不限制该版本此前的处理阶段。 */
    @Update("""
            UPDATE document_version
            SET status = 'DELETING', cleanup_status = 'PENDING', cleanup_retry_count = 0,
                cleanup_failure_reason = NULL, update_by = #{operator}, update_time = NOW()
            WHERE document_id = #{documentId} AND document_version_id = #{documentVersionId}
              AND status <> 'DELETING'
            """)
    int markDeletingForDocument(@Param("documentId") Long documentId,
                                @Param("documentVersionId") Long documentVersionId,
                                @Param("operator") String operator);
}
