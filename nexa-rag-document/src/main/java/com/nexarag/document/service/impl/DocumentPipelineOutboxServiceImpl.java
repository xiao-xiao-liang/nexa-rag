package com.nexarag.document.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.nexarag.common.exception.ServiceException;
import com.nexarag.document.config.DocumentPipelineOutboxProperties;
import com.nexarag.document.model.entity.DocumentTaskOutboxDO;
import com.nexarag.document.enums.OutboxPublishStatus;
import com.nexarag.document.mapper.DocumentPipelineOutboxMapper;
import com.nexarag.document.service.DocumentPipelineOutboxService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 文档流水线Outbox服务实现，使用数据库条件更新完成多实例发布抢占。
 */
@Service
@RequiredArgsConstructor
public class DocumentPipelineOutboxServiceImpl
        extends ServiceImpl<DocumentPipelineOutboxMapper, DocumentTaskOutboxDO>
        implements DocumentPipelineOutboxService {

    private static final int MAX_FAILURE_REASON_LENGTH = 1024;

    private final DocumentPipelineOutboxMapper outboxMapper;
    private final DocumentPipelineOutboxProperties properties;

    @Override
    public List<DocumentTaskOutboxDO> claimPublishableMessages(String lockOwner, LocalDateTime now) {
        if (lockOwner == null || lockOwner.isBlank() || now == null) {
            throw new ServiceException("Outbox发布抢占参数不能为空");
        }

        // 1. 查询待发布消息和抢占超时消息
        LocalDateTime expiredLockTime = now.minusSeconds(properties.getPublishingTimeoutSeconds());
        List<DocumentTaskOutboxDO> candidates = outboxMapper.selectPublishable(
                now, expiredLockTime, properties.getBatchSize());

        // 2. 逐条使用原状态和原锁时间竞争发布权
        List<DocumentTaskOutboxDO> claimed = new ArrayList<>();
        for (DocumentTaskOutboxDO candidate : candidates) {
            int affected = outboxMapper.claim(candidate.getOutboxId(), candidate.getPublishStatus(),
                    candidate.getLockTime(), lockOwner, now);
            if (affected > 0) {
                candidate.setPublishStatus(OutboxPublishStatus.PUBLISHING);
                candidate.setLockOwner(lockOwner);
                candidate.setLockTime(now);
                claimed.add(candidate);
            }
        }
        return claimed;
    }

    @Override
    public void markPublished(Long outboxId) {
        // 1. 仅允许发布中的消息进入已发布状态
        if (outboxMapper.updatePublished(outboxId, LocalDateTime.now()) <= 0) {
            throw new ServiceException("更新Outbox发布成功状态失败，outboxId=" + outboxId);
        }
    }

    @Override
    public void markPublishFailed(Long outboxId, String failureReason) {
        DocumentTaskOutboxDO outbox = outboxMapper.selectById(outboxId);
        if (outbox == null) {
            throw new ServiceException("Outbox消息不存在，outboxId=" + outboxId);
        }

        // 1. 增加发布次数并判断是否达到最终失败上限
        int currentRetryCount = outbox.getPublishRetryCount() == null ? 0 : outbox.getPublishRetryCount();
        int nextRetryCount = currentRetryCount + 1;
        boolean finalFailure = nextRetryCount >= properties.getMaxPublishRetries();
        OutboxPublishStatus status = finalFailure ? OutboxPublishStatus.FAILED : OutboxPublishStatus.PENDING;
        LocalDateTime nextRetryTime = finalFailure ? null : LocalDateTime.now().plusSeconds(retryDelay(nextRetryCount));

        // 2. 清理发布锁并保存安全截断后的失败原因
        int affected = outboxMapper.updatePublishFailure(outboxId, status, nextRetryCount,
                nextRetryTime, truncateFailureReason(failureReason));
        if (affected <= 0) {
            throw new ServiceException("更新Outbox发布失败状态失败，outboxId=" + outboxId);
        }
    }

    private long retryDelay(int retryCount) {
        int exponent = Math.min(Math.max(retryCount - 1, 0), 20);
        long multiplier = 1L << exponent;
        long delay = properties.getInitialRetryDelaySeconds() * multiplier;
        return Math.min(delay, properties.getMaxRetryDelaySeconds());
    }

    private String truncateFailureReason(String failureReason) {
        if (failureReason == null) {
            return "未知发布异常";
        }
        return failureReason.length() <= MAX_FAILURE_REASON_LENGTH
                ? failureReason
                : failureReason.substring(0, MAX_FAILURE_REASON_LENGTH);
    }
}
