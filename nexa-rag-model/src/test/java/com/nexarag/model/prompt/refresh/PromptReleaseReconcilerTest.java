package com.nexarag.model.prompt.refresh;

import com.nexarag.model.toolkits.prompt.PromptReleaseReconciler;
import com.nexarag.model.toolkits.prompt.PromptSnapshotCache;
import com.nexarag.model.entity.prompt.PromptDefinition;
import com.nexarag.model.entity.prompt.PromptRelease;
import com.nexarag.model.mapper.PromptDefinitionMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Prompt 发布代次对账器测试。
 */
class PromptReleaseReconcilerTest {

    /**
     * 验证更高代次事件只失效对应 Prompt 的当前发布缓存。
     */
    @Test
    void shouldInvalidateCurrentCacheWhenReceivingHigherRevision() {
        PromptSnapshotCache cache = new PromptSnapshotCache();
        AtomicInteger loadCount = new AtomicInteger();
        cache.getOrLoadCurrent("chat.answer", () -> release(loadCount.incrementAndGet()));
        PromptDefinitionMapper definitionMapper = mock(PromptDefinitionMapper.class);
        PromptReleaseReconciler reconciler = new PromptReleaseReconciler(definitionMapper, cache);

        // 1. 接收更高发布代次的刷新事件。
        boolean invalidated = reconciler.onReleaseChanged(new PromptReleaseChangedMessage("chat.answer", 2L, 2L));

        // 2. 验证当前发布缓存被精确删除。
        assertThat(invalidated).isTrue();
        cache.getOrLoadCurrent("chat.answer", () -> release(loadCount.incrementAndGet()));
        assertThat(loadCount).hasValue(2);
    }

    /**
     * 验证乱序或重复的旧事件不会删除已观察到新代次后的缓存。
     */
    @Test
    void shouldIgnoreOldReleaseChangedMessage() {
        PromptSnapshotCache cache = new PromptSnapshotCache();
        AtomicInteger loadCount = new AtomicInteger();
        cache.getOrLoadCurrent("chat.answer", () -> release(loadCount.incrementAndGet()));
        PromptReleaseReconciler reconciler = new PromptReleaseReconciler(mock(PromptDefinitionMapper.class), cache);
        reconciler.onReleaseChanged(new PromptReleaseChangedMessage("chat.answer", 3L, 3L));
        cache.getOrLoadCurrent("chat.answer", () -> release(loadCount.incrementAndGet()));

        // 1. 接收低于已观察代次的旧事件。
        boolean invalidated = reconciler.onReleaseChanged(new PromptReleaseChangedMessage("chat.answer", 2L, 2L));

        // 2. 验证缓存未被旧事件删除。
        assertThat(invalidated).isFalse();
        cache.getOrLoadCurrent("chat.answer", () -> release(loadCount.incrementAndGet()));
        assertThat(loadCount).hasValue(2);
    }

    /**
     * 验证漏收消息后，对账仅依据数据库中的轻量发布代次删除旧缓存。
     */
    @Test
    void shouldInvalidateCacheWhenDatabaseRevisionIsHigherDuringReconciliation() {
        PromptSnapshotCache cache = new PromptSnapshotCache();
        AtomicInteger loadCount = new AtomicInteger();
        cache.getOrLoadCurrent("chat.answer", () -> release(loadCount.incrementAndGet()));
        PromptDefinitionMapper definitionMapper = mock(PromptDefinitionMapper.class);
        when(definitionMapper.selectEnabledReleaseRevisions()).thenReturn(List.of(PromptDefinition.builder()
                .promptCode("chat.answer").currentReleaseRevision(5L).build()));
        PromptReleaseReconciler reconciler = new PromptReleaseReconciler(definitionMapper, cache);

        // 1. 执行数据库发布代次对账。
        reconciler.reconcile();

        // 2. 验证只失效发现代次差异的当前发布缓存。
        cache.getOrLoadCurrent("chat.answer", () -> release(loadCount.incrementAndGet()));
        assertThat(loadCount).hasValue(2);
        verify(definitionMapper).selectEnabledReleaseRevisions();
    }

    private PromptRelease release(int releaseRevision) {
        return PromptRelease.builder().releaseRevision((long) releaseRevision).build();
    }
}
