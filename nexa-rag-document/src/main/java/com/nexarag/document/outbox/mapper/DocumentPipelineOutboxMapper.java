package com.nexarag.document.outbox.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.nexarag.document.outbox.entity.DocumentPipelineOutbox;
import com.nexarag.document.outbox.enums.OutboxPublishStatus;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 文档流水线Outbox数据访问接口。
 */
@Mapper
public interface DocumentPipelineOutboxMapper extends BaseMapper<DocumentPipelineOutbox> {

    /**
     * 查询待发布或抢占超时的Outbox消息。
     */
    @Select("""
            SELECT
                outbox_id, document_id, process_id, message_key, topic, message_body,
                publish_status, publish_retry_count, next_retry_time, lock_owner, lock_time,
                published_time, failure_reason, create_time, update_time
            FROM document_pipeline_outbox
            WHERE (publish_status = 'PENDING' AND (next_retry_time IS NULL OR next_retry_time <= #{now}))
               OR (publish_status = 'PUBLISHING' AND lock_time <= #{expiredLockTime})
            ORDER BY create_time ASC
            LIMIT #{limit}
            """)
    List<DocumentPipelineOutbox> selectPublishable(@Param("now") LocalDateTime now,
                                                   @Param("expiredLockTime") LocalDateTime expiredLockTime,
                                                   @Param("limit") int limit);

    /**
     * 使用候选记录原状态和锁时间抢占发布权。
     */
    @Update("""
            UPDATE document_pipeline_outbox
            SET publish_status = 'PUBLISHING', lock_owner = #{lockOwner}, lock_time = #{lockTime}
            WHERE outbox_id = #{outboxId}
              AND publish_status = #{expectedStatus}
              AND (#{expectedStatus} <> 'PUBLISHING' OR lock_time = #{expectedLockTime})
            """)
    int claim(@Param("outboxId") Long outboxId,
              @Param("expectedStatus") OutboxPublishStatus expectedStatus,
              @Param("expectedLockTime") LocalDateTime expectedLockTime,
              @Param("lockOwner") String lockOwner,
              @Param("lockTime") LocalDateTime lockTime);

    /**
     * 更新消息发布成功状态。
     */
    @Update("""
            UPDATE document_pipeline_outbox
            SET publish_status = 'PUBLISHED', published_time = #{publishedTime},
                lock_owner = NULL, lock_time = NULL, failure_reason = NULL
            WHERE outbox_id = #{outboxId} AND publish_status = 'PUBLISHING'
            """)
    int updatePublished(@Param("outboxId") Long outboxId,
                        @Param("publishedTime") LocalDateTime publishedTime);

    /**
     * 更新消息发布失败后的状态和重试信息。
     */
    @Update("""
            UPDATE document_pipeline_outbox
            SET publish_status = #{status}, publish_retry_count = #{retryCount},
                next_retry_time = #{nextRetryTime}, failure_reason = #{failureReason},
                lock_owner = NULL, lock_time = NULL
            WHERE outbox_id = #{outboxId} AND publish_status = 'PUBLISHING'
            """)
    int updatePublishFailure(@Param("outboxId") Long outboxId,
                             @Param("status") OutboxPublishStatus status,
                             @Param("retryCount") int retryCount,
                             @Param("nextRetryTime") LocalDateTime nextRetryTime,
                             @Param("failureReason") String failureReason);
}
