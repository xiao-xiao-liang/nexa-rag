package com.nexarag.workflow.stream;

import com.alibaba.cloud.ai.graph.GraphResponse;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.streaming.StreamingOutput;
import com.nexarag.model.gateway.chat.ChatModelStreamResponse;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.util.Map;

import static com.nexarag.workflow.constants.ChatWorkflowStateKeys.ASSISTANT_CONTENT;
import static com.nexarag.workflow.constants.ChatWorkflowStateKeys.STREAM_STATUS;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Chat 流式转换工具测试，验证正文事件和最终状态回写。
 */
class ChatWorkflowStreamingUtilTest {

    @Test
    void toGraphStreamShouldEmitTokenEventsAndCompletedState() {
        OverAllState state = new OverAllState(Map.of());

        Flux<GraphResponse<StreamingOutput<ChatStreamEvent>>> stream = ChatWorkflowStreamingUtil.toGraphStream(
                ChatWorkflowStreamingUtilTest.class, state,
                Flux.just(ChatModelStreamResponse.message("你"), ChatModelStreamResponse.message("好"),
                        ChatModelStreamResponse.done("STOP")));

        StepVerifier.create(stream)
                .assertNext(response -> assertToken(response, "你"))
                .assertNext(response -> assertToken(response, "好"))
                .assertNext(response -> {
                    assertThat(response.isDone()).isTrue();
                    assertThat(response.resultValue()).containsInstanceOf(Map.class);
                    @SuppressWarnings("unchecked")
                    Map<String, Object> finalState = (Map<String, Object>) response.resultValue().orElseThrow();
                    assertThat(finalState).containsEntry(ASSISTANT_CONTENT, "你好");
                    assertThat(finalState).containsEntry(STREAM_STATUS, "COMPLETED");
                })
                .verifyComplete();
    }

    @Test
    void toGraphStreamShouldEmitErrorEventAndFailedState() {
        OverAllState state = new OverAllState(Map.of());

        Flux<GraphResponse<StreamingOutput<ChatStreamEvent>>> stream = ChatWorkflowStreamingUtil.toGraphStream(
                ChatWorkflowStreamingUtilTest.class, state,
                Flux.just(ChatModelStreamResponse.message("部分"),
                        ChatModelStreamResponse.error("MODEL_UNAVAILABLE", "模型不可用")));

        StepVerifier.create(stream)
                .assertNext(response -> assertToken(response, "部分"))
                .assertNext(response -> {
                    ChatStreamEvent event = response.getOutput().join().getOriginData();
                    assertThat(event.type()).isEqualTo(ChatStreamEventType.ERROR);
                    assertThat(event.errorCode()).isEqualTo("MODEL_UNAVAILABLE");
                })
                .assertNext(response -> {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> finalState = (Map<String, Object>) response.resultValue().orElseThrow();
                    assertThat(finalState).containsEntry(ASSISTANT_CONTENT, "部分");
                    assertThat(finalState).containsEntry(STREAM_STATUS, "FAILED");
                })
                .verifyComplete();
    }

    private void assertToken(GraphResponse<StreamingOutput<ChatStreamEvent>> response, String content) {
        ChatStreamEvent event = response.getOutput().join().getOriginData();
        assertThat(event.type()).isEqualTo(ChatStreamEventType.TOKEN);
        assertThat(event.content()).isEqualTo(content);
    }
}
