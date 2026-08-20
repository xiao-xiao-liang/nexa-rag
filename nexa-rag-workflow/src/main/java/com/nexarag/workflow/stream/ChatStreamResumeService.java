package com.nexarag.workflow.stream;

import com.nexarag.common.exception.ClientException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 校验生成任务归属并读取 Redis 中可重放流事件。
 */
@Service
@RequiredArgsConstructor
public class ChatStreamResumeService {

    private final ChatGenerationCancellationHandler cancellationHandler;
    private final ChatStreamEventBuffer eventBuffer;

    /**
     * 恢复指定用户可访问的生成任务事件。
     *
     * @param generationId 生成任务 ID
     * @param userId 当前用户 ID
     * @param lastEventVersion 客户端已接收最大版本
     * @return 待重放事件
     */
    public List<ChatStreamEvent> resume(String generationId, String userId, long lastEventVersion) {
        // 1. 校验生成任务归属，避免跨用户读取回答内容
        String ownerId = cancellationHandler.findOwner(generationId);
        if (ownerId == null || !ownerId.equals(userId)) {
            throw new ClientException("生成任务不存在或不属于当前用户");
        }

        // 2. 返回版本严格递增的重放事件
        return eventBuffer.eventsAfter(generationId, lastEventVersion);
    }
}
