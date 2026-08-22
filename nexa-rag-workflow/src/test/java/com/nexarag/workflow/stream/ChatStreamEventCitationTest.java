package com.nexarag.workflow.stream;

import com.nexarag.chat.domain.ChatCitationSummaryVO;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * SSE 引用公开投影测试。
 */
class ChatStreamEventCitationTest {

    @Test
    void shouldKeepCitationIdsWhenAssigningEventVersion() {
        ChatStreamEvent event = new ChatStreamEvent(ChatStreamEventType.CITATIONS, null,
                "c1", "t1", "g1", "m1", null, null, 0L, List.of(),
                List.of(new ChatCitationSummaryVO(1), new ChatCitationSummaryVO(2)));

        ChatStreamEvent versioned = event.withEventVersion(3L);

        assertThat(versioned.eventVersion()).isEqualTo(3L);
        assertThat(versioned.citations()).extracting(ChatCitationSummaryVO::citationId)
                .containsExactly(1, 2);
    }
}
