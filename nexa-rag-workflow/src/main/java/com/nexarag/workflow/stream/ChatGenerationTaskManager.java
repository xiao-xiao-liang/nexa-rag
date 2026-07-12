package com.nexarag.workflow.stream;

import org.springframework.stereotype.Component;
import reactor.core.Disposable;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Chat 生成任务管理器，负责本地流绑定、取消鉴权和最终化幂等控制。
 */
@Component
public class ChatGenerationTaskManager {

    private final ChatGenerationCancellationHandler cancellationHandler;
    private final Map<String, GenerationTask> tasks = new ConcurrentHashMap<>();

    /**
     * 创建任务管理器并注册跨实例取消回调。
     *
     * @param cancellationHandler Redis 取消协调器
     */
    public ChatGenerationTaskManager(ChatGenerationCancellationHandler cancellationHandler) {
        this.cancellationHandler = cancellationHandler;
        this.cancellationHandler.registerListener(this::cancelFromRemote);
    }

    /**
     * 注册生成任务。
     *
     * @param generationId 生成任务 ID
     * @param userId 用户 ID
     * @param conversationId 会话 ID
     * @param accumulator 生成累积器
     * @param cancellationFinalizer 取消最终化回调
     */
    public void register(String generationId, String userId, String conversationId,
                         ChatGenerationAccumulator accumulator, Runnable cancellationFinalizer) {
        // 1. 保存本地任务和跨实例鉴权信息
        GenerationTask task = new GenerationTask(userId, conversationId, accumulator, cancellationFinalizer);
        tasks.put(generationId, task);
        cancellationHandler.registerOwner(generationId, userId);

        // 2. 处理先收到取消标记、后注册任务的情况
        if (cancellationHandler.isCancelled(generationId)) {
            cancelTask(task);
        }
    }

    /**
     * 将模型订阅绑定到生成任务，已取消时立即释放订阅。
     *
     * @param generationId 生成任务 ID
     * @param disposable 模型流订阅
     */
    public void bind(String generationId, Disposable disposable) {
        GenerationTask task = tasks.get(generationId);
        if (task == null) {
            disposable.dispose();
            return;
        }
        task.disposable = disposable;
        if (task.cancelled.get()) {
            disposable.dispose();
        }
    }

    /**
     * 按用户身份取消生成任务。
     *
     * @param generationId 生成任务 ID
     * @param userId 当前用户 ID
     * @return 是否有权取消该任务
     */
    public boolean cancel(String generationId, String userId) {
        GenerationTask task = tasks.get(generationId);
        if (task != null) {
            if (!task.userId.equals(userId)) {
                return false;
            }
            if (cancelTask(task)) {
                cancellationHandler.publishCancellation(generationId, userId);
            }
            return true;
        }

        String owner = cancellationHandler.findOwner(generationId);
        if (!userId.equals(owner)) {
            return false;
        }
        cancellationHandler.publishCancellation(generationId, userId);
        return true;
    }

    /**
     * 处理其他实例发布的取消通知。
     *
     * @param generationId 生成任务 ID
     * @param userId 任务所属用户 ID
     */
    public void cancelFromRemote(String generationId, String userId) {
        GenerationTask task = tasks.get(generationId);
        if (task != null && task.userId.equals(userId)) {
            cancelTask(task);
        }
    }

    private boolean cancelTask(GenerationTask task) {
        if (!task.cancelled.compareAndSet(false, true)) {
            return false;
        }
        Disposable disposable = task.disposable;
        if (disposable != null) {
            disposable.dispose();
        }
        if (task.finalized.compareAndSet(false, true)) {
            task.cancellationFinalizer.run();
        }
        return true;
    }

    private static final class GenerationTask {
        private final String userId;
        private final String conversationId;
        private final ChatGenerationAccumulator accumulator;
        private final Runnable cancellationFinalizer;
        private final AtomicBoolean cancelled = new AtomicBoolean();
        private final AtomicBoolean finalized = new AtomicBoolean();
        private volatile Disposable disposable;

        private GenerationTask(String userId, String conversationId,
                               ChatGenerationAccumulator accumulator, Runnable cancellationFinalizer) {
            this.userId = userId;
            this.conversationId = conversationId;
            this.accumulator = accumulator;
            this.cancellationFinalizer = cancellationFinalizer;
        }
    }
}
