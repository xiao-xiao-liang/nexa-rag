package com.nexarag.workflow.stream;

import org.junit.jupiter.api.Test;
import reactor.core.Disposable;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Chat 生成任务管理器测试，验证取消鉴权、竞态和幂等最终化。
 */
class ChatGenerationTaskManagerTest {

    @Test
    void cancelShouldFinalizeOnceAndDisposeLateBoundStream() {
        ChatGenerationCancellationHandler cancellationHandler = mock(ChatGenerationCancellationHandler.class);
        ChatGenerationTaskManager taskManager = new ChatGenerationTaskManager(cancellationHandler);
        AtomicInteger finalizedCount = new AtomicInteger();
        ChatGenerationAccumulator accumulator = new ChatGenerationAccumulator();
        taskManager.register("g1", "u1", "c1", accumulator, finalizedCount::incrementAndGet);

        assertThat(taskManager.cancel("g1", "u1")).isTrue();
        assertThat(taskManager.cancel("g1", "u1")).isTrue();
        Disposable disposable = mock(Disposable.class);
        taskManager.bind("g1", disposable);

        assertThat(finalizedCount).hasValue(1);
        verify(disposable).dispose();
        verify(cancellationHandler).publishCancellation("g1", "u1");
    }

    @Test
    void cancelShouldRejectDifferentUser() {
        ChatGenerationTaskManager taskManager = new ChatGenerationTaskManager(
                mock(ChatGenerationCancellationHandler.class));
        taskManager.register("g1", "u1", "c1", new ChatGenerationAccumulator(), () -> { });

        assertThat(taskManager.cancel("g1", "u2")).isFalse();
    }

    @Test
    void remoteCancellationMessageShouldCancelLocalTask() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        ChatGenerationCancellationHandler cancellationHandler =
                new ChatGenerationCancellationHandler(redisTemplate);
        ChatGenerationTaskManager taskManager = new ChatGenerationTaskManager(cancellationHandler);
        AtomicInteger finalizedCount = new AtomicInteger();
        taskManager.register("g1", "u1", "c1", new ChatGenerationAccumulator(), finalizedCount::incrementAndGet);
        Disposable disposable = mock(Disposable.class);
        taskManager.bind("g1", disposable);

        cancellationHandler.onMessage("g1:u1");

        assertThat(finalizedCount).hasValue(1);
        verify(disposable).dispose();
    }
}
