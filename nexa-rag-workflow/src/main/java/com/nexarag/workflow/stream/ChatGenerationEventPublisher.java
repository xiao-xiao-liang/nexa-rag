package com.nexarag.workflow.stream;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.util.Map;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import static com.nexarag.workflow.constants.ChatGenerationRedisConstants.MAX_BUFFERED_EVENTS;

/**
 * 将生成事件写入 Redis 并分发给本实例 SSE 订阅者。
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ChatGenerationEventPublisher {

    private final ChatStreamEventBuffer eventBuffer;
    private final ObjectMapper objectMapper;
    private final Map<String, LocalGenerationStream> localStreams = new ConcurrentHashMap<>();

    /**
     * 发布事件并立即通知本实例订阅者。
     *
     * @param event 待发布事件
     * @return 已分配版本的事件
     */
    public ChatStreamEvent publish(ChatStreamEvent event) {
        ChatStreamEvent persistedEvent = eventBuffer.publish(event);
        emitIfNew(persistedEvent);
        return persistedEvent;
    }

    /**
     * 打开某个生成任务的本实例实时事件订阅。
     *
     * @param generationId 生成任务 ID
     * @return 实时事件流
     */
    public Flux<ChatStreamEvent> open(String generationId) {
        LocalGenerationStream stream = localStreams.computeIfAbsent(generationId,
                ignored -> new LocalGenerationStream());
        return stream.sink.asFlux();
    }

    /**
     * 消费 Redis Pub/Sub 的事件载荷并通知本实例 SSE 订阅者。
     *
     * @param payload Redis 事件 JSON
     */
    public void acceptRedisPayload(String payload) {
        try {
            ChatStreamEvent event = objectMapper.readValue(payload, ChatStreamEvent.class);
            emitIfNew(event);
            if (isTerminal(event)) {
                complete(event.generationId());
            }
        } catch (JsonProcessingException exception) {
            log.warn("忽略无法解析的生成流事件", exception);
        }
    }

    /**
     * 关闭生成任务的本实例实时订阅。
     *
     * @param generationId 生成任务 ID
     */
    public void complete(String generationId) {
        LocalGenerationStream stream = localStreams.remove(generationId);
        if (stream != null) {
            synchronized (stream.monitor) {
                stream.sink.tryEmitComplete();
            }
        }
    }

    /**
     * 将重放完成的版本标记为已交付，避免紧邻的 Pub/Sub 事件重复进入本地流。
     *
     * @param generationId 生成任务 ID
     * @param eventVersion 已交付的版本
     */
    public void markDelivered(String generationId, long eventVersion) {
        LocalGenerationStream stream = localStreams.get(generationId);
        if (stream == null) {
            return;
        }
        synchronized (stream.monitor) {
            stream.deliveredVersion = Math.max(stream.deliveredVersion, eventVersion);
        }
    }

    /**
     * 序列化仅包含工具名称和状态的终态投影，用于历史消息持久化。
     *
     * @param operations 工具调用展示快照
     * @return JSON 字符串
     */
    public String serializeOperations(List<ChatToolOperationDTO> operations) {
        try {
            return objectMapper.writeValueAsString(operations == null ? List.of() : operations);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("工具调用快照序列化失败", exception);
        }
    }

    private void emitIfNew(ChatStreamEvent event) {
        if (event.generationId() == null || event.generationId().isBlank()) {
            return;
        }
        LocalGenerationStream stream = localStreams.get(event.generationId());
        if (stream == null) {
            return;
        }
        synchronized (stream.monitor) {
            if (event.eventVersion() > 0 && event.eventVersion() <= stream.deliveredVersion) {
                return;
            }
            Sinks.EmitResult emitResult = stream.sink.tryEmitNext(event);
            if (!emitResult.isSuccess()) {
                log.warn("生成流事件本地投递失败，generationId={}，eventType={}，eventVersion={}，result={}",
                        event.generationId(), event.type(), event.eventVersion(), emitResult);
                return;
            }
            if (event.eventVersion() > 0) {
                stream.deliveredVersion = event.eventVersion();
            }
        }
    }

    private boolean isTerminal(ChatStreamEvent event) {
        return event.type() == ChatStreamEventType.COMPLETE
                || event.type() == ChatStreamEventType.ERROR
                || event.type() == ChatStreamEventType.CANCELLED;
    }

    private static final class LocalGenerationStream {
        private final Object monitor = new Object();
        private final Sinks.Many<ChatStreamEvent> sink = Sinks.many().replay().limit(MAX_BUFFERED_EVENTS);
        private long deliveredVersion;
    }
}
