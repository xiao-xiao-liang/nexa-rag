package com.nexarag.workflow.stream;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.util.List;

/**
 * Chat 生成事件发布器测试，验证跨实例终态事件会结束本地 SSE 流。
 */
class ChatGenerationEventPublisherTest {

    @Test
    void redisTerminalEventShouldCompleteLocalStream() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        ChatGenerationEventPublisher publisher = new ChatGenerationEventPublisher(new InMemoryEventBuffer(), objectMapper);
        ChatStreamEvent event = new ChatStreamEvent(ChatStreamEventType.ERROR, null,
                "c1", "t1", "g1", "m1", "MODEL_ERROR", "模型不可用").withEventVersion(1L);
        String payload = objectMapper.writeValueAsString(event);

        StepVerifier.setDefaultTimeout(Duration.ofSeconds(1));
        try {
            StepVerifier.create(publisher.open("g1"))
                    .then(() -> publisher.acceptRedisPayload(payload))
                    .expectNext(event)
                    .verifyComplete();
        } finally {
            StepVerifier.resetDefaultTimeout();
        }
    }

    private static final class InMemoryEventBuffer implements ChatStreamEventBuffer {

        @Override
        public ChatStreamEvent publish(ChatStreamEvent event) {
            return event;
        }

        @Override
        public List<ChatStreamEvent> eventsAfter(String generationId, long eventVersion) {
            return List.of();
        }
    }
}
