package com.nexarag.model.toolkits.prompt;

import com.nexarag.model.entity.prompt.PromptDefinition;
import com.nexarag.model.mapper.PromptDefinitionMapper;
import com.nexarag.model.prompt.refresh.PromptReleaseChangedMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Prompt 发布代次对账器，负责处理刷新事件和补偿 Redis Pub/Sub 漏收消息。
 */
@Component
@RequiredArgsConstructor
public class PromptReleaseReconciler {

    private final PromptDefinitionMapper definitionMapper;
    private final PromptSnapshotCache snapshotCache;
    private final Map<String, Long> observedRevisions = new ConcurrentHashMap<>();

    /**
     * 处理发布变更消息，仅在收到更高代次时精确失效当前发布缓存。
     *
     * @param message 发布变更消息
     * @return 是否执行了缓存失效
     */
    public boolean onReleaseChanged(PromptReleaseChangedMessage message) {
        if (message == null || message.promptCode() == null || message.releaseRevision() == null) {
            return false;
        }
        // 1. 使用原子计算比较并记录已观察到的最大发布代次。
        boolean[] invalidated = {false};
        observedRevisions.compute(message.promptCode(), (promptCode, observedRevision) -> {
            if (observedRevision == null || message.releaseRevision() > observedRevision) {
                // 2. 仅删除当前发布快照，历史不可变版本缓存仍可复用。
                snapshotCache.invalidateCurrent(promptCode);
                invalidated[0] = true;
                return message.releaseRevision();
            }
            return observedRevision;
        });
        return invalidated[0];
    }

    /**
     * 从数据库轻量查询当前发布代次，补偿漏收的刷新消息。
     */
    public void reconcile() {
        // 1. 只读取 Prompt 编码和当前发布代次，不读取模板正文。
        for (PromptDefinition definition : definitionMapper.selectEnabledReleaseRevisions()) {
            // 2. 复用事件处理逻辑，保证代次比较和缓存失效语义一致。
            onReleaseChanged(new PromptReleaseChangedMessage(definition.getPromptCode(), null,
                    definition.getCurrentReleaseRevision()));
        }
    }
}
