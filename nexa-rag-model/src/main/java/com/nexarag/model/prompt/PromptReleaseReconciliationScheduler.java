package com.nexarag.model.prompt;

import com.nexarag.model.toolkits.prompt.PromptReleaseReconciler;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Prompt 发布代次定时对账任务，补偿 Redis Pub/Sub 的瞬时消息丢失。
 */
@Component
@RequiredArgsConstructor
public class PromptReleaseReconciliationScheduler {

    private final PromptReleaseReconciler reconciler;

    /**
     * 按配置间隔比对数据库发布代次与本机已观察代次。
     */
    @Scheduled(fixedDelayString = "${nexa.prompt.refresh.reconcile-interval-ms:60000}")
    public void reconcile() {
        // 1. 触发轻量发布代次查询和精确缓存失效。
        reconciler.reconcile();
    }
}
