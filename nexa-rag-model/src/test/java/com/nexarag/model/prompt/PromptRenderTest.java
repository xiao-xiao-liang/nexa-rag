package com.nexarag.model.prompt;

import com.nexarag.common.exception.ClientException;
import com.nexarag.model.prompt.domain.PromptExecutionSnapshot;
import com.nexarag.model.prompt.domain.PromptVariableSchema;
import com.nexarag.model.toolkits.prompt.PromptRender;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Prompt 渲染服务测试。
 */
class PromptRenderTest {

    @Test
    void shouldKeepOldSnapshotContentAfterNewRelease() {
        PromptExecutionSnapshot snapshot = PromptExecutionSnapshot.of(Map.of("chat.answer",
                new PromptExecutionSnapshot.PromptSnapshot("chat.answer", 101L, 11L, 1L,
                        "旧版本：{{query}}", PromptVariableSchema.of(List.of("query"), List.of("query")))));
        PromptRender service = new PromptRender();

        PromptRender.RenderedPrompt rendered = service.render(snapshot, "chat.answer", Map.of("query", "RAG"));

        assertThat(rendered.content()).isEqualTo("旧版本：RAG");
        assertThat(rendered.versionId()).isEqualTo(101L);
    }

    @Test
    void shouldRejectMissingRequiredVariable() {
        PromptExecutionSnapshot snapshot = PromptExecutionSnapshot.of(Map.of("chat.answer",
                new PromptExecutionSnapshot.PromptSnapshot("chat.answer", 101L, 11L, 1L,
                        "问题：{{query}}", PromptVariableSchema.of(List.of("query"), List.of("query")))));

        assertThatThrownBy(() -> new PromptRender().render(snapshot, "chat.answer", Map.of()))
                .isInstanceOf(ClientException.class)
                .hasMessageContaining("query");
    }

    @Test
    void shouldRenderRawContentWithoutHtmlEscaping() {
        PromptExecutionSnapshot snapshot = PromptExecutionSnapshot.of(Map.of("chat.answer",
                new PromptExecutionSnapshot.PromptSnapshot("chat.answer", 101L, 11L, 1L,
                        "证据：{{evidence}}", PromptVariableSchema.of(List.of("evidence"), List.of("evidence")))));

        PromptRender.RenderedPrompt rendered = new PromptRender()
                .render(snapshot, "chat.answer", Map.of("evidence", "<b>原始证据</b>"));

        assertThat(rendered.content()).isEqualTo("证据：<b>原始证据</b>");
    }
}
