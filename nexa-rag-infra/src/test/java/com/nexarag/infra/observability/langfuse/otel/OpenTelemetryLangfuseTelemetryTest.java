package com.nexarag.infra.observability.langfuse.otel;

import com.nexarag.infra.observability.langfuse.LangfuseSpanScope;
import com.nexarag.infra.observability.langfuse.LangfuseTelemetry;
import com.nexarag.infra.observability.langfuse.LangfuseTraceScope;
import com.nexarag.infra.observability.langfuse.NoopLangfuseTelemetry;
import com.nexarag.infra.observability.langfuse.model.LangfuseObservationType;
import com.nexarag.infra.observability.langfuse.model.LangfuseTraceCommand;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * OpenTelemetry Langfuse 遥测实现测试。
 */
class OpenTelemetryLangfuseTelemetryTest {

    private final InMemorySpanExporter exporter = InMemorySpanExporter.create();
    private final SdkTracerProvider tracerProvider = SdkTracerProvider.builder()
            .addSpanProcessor(SimpleSpanProcessor.create(exporter))
            .build();

    @AfterEach
    void tearDown() {
        tracerProvider.close();
    }

    @Test
    void shouldRecordTraceIdentityWithoutContentAttributes() {
        OpenTelemetry openTelemetry = OpenTelemetrySdk.builder()
                .setTracerProvider(tracerProvider)
                .build();
        LangfuseTelemetry telemetry = new OpenTelemetryLangfuseTelemetry(
                openTelemetry.getTracer("nexa-rag-langfuse"), "local");

        LangfuseTraceScope scope = telemetry.startTrace(new LangfuseTraceCommand(
                "rag.chat", "business-trace-001", "conversation-001", "user-001",
                Map.of("nexa.generation_id", "generation-001", "nexa.tenant_id", "tenant-001")));
        scope.close();

        assertThat(exporter.getFinishedSpanItems()).singleElement().satisfies(span -> {
            assertThat(span.getName()).isEqualTo("rag.chat");
            assertThat(span.getAttributes().asMap())
                    .containsEntry(io.opentelemetry.api.common.AttributeKey.stringKey("langfuse.trace.name"), "rag.chat")
                    .containsEntry(io.opentelemetry.api.common.AttributeKey.stringKey("langfuse.user.id"), "user-001")
                    .containsEntry(io.opentelemetry.api.common.AttributeKey.stringKey("langfuse.session.id"), "conversation-001")
                    .containsEntry(io.opentelemetry.api.common.AttributeKey.stringKey("nexa.trace_id"), "business-trace-001")
                    .containsEntry(io.opentelemetry.api.common.AttributeKey.stringKey("nexa.generation_id"), "generation-001")
                    .doesNotContainKeys(
                            io.opentelemetry.api.common.AttributeKey.stringKey("input"),
                            io.opentelemetry.api.common.AttributeKey.stringKey("output"),
                            io.opentelemetry.api.common.AttributeKey.stringKey("prompt"));
        });
    }

    @Test
    void shouldDoNothingWhenTelemetryIsDisabled() {
        LangfuseTraceScope scope = NoopLangfuseTelemetry.INSTANCE.startTrace(new LangfuseTraceCommand(
                "rag.chat", "business-trace-001", null, "user-001", Map.of()));

        scope.close();

        assertThat(exporter.getFinishedSpanItems()).isEmpty();
    }

    @Test
    void shouldCreateSafeChildSpanUnderProvidedTraceContext() {
        OpenTelemetry openTelemetry = OpenTelemetrySdk.builder()
                .setTracerProvider(tracerProvider)
                .build();
        LangfuseTelemetry telemetry = new OpenTelemetryLangfuseTelemetry(
                openTelemetry.getTracer("nexa-rag-langfuse"), "local");
        LangfuseTraceScope trace = telemetry.startTrace(new LangfuseTraceCommand(
                "rag.chat", "business-trace-001", null, null, Map.of("nexa.generation_id", "generation-001")));

        LangfuseSpanScope span = telemetry.startSpan("rag.retrieval", Map.of(
                "nexa.generation_id", "generation-001", "nexa.retrieval.candidate_count", 12), trace.context());
        span.close();
        trace.close();

        assertThat(exporter.getFinishedSpanItems()).filteredOn(item -> item.getName().equals("rag.retrieval"))
                .singleElement().satisfies(item -> assertThat(item.getAttributes().asMap())
                        .containsEntry(io.opentelemetry.api.common.AttributeKey.stringKey("langfuse.observation.type"),
                                "span")
                        .containsEntry(io.opentelemetry.api.common.AttributeKey.longKey("nexa.retrieval.candidate_count"),
                                12L)
                        .doesNotContainKeys(io.opentelemetry.api.common.AttributeKey.stringKey("input"),
                                io.opentelemetry.api.common.AttributeKey.stringKey("output"),
                                io.opentelemetry.api.common.AttributeKey.stringKey("prompt")));
    }

    @Test
    void shouldPropagateTraceAttributesAndUseSpecificObservationType() {
        OpenTelemetry openTelemetry = OpenTelemetrySdk.builder()
                .setTracerProvider(tracerProvider)
                .build();
        LangfuseTelemetry telemetry = new OpenTelemetryLangfuseTelemetry(
                openTelemetry.getTracer("nexa-rag-langfuse"), "local");
        LangfuseTraceScope trace = telemetry.startTrace(new LangfuseTraceCommand(
                "rag.chat", "business-trace-001", "conversation-001", "user-001", Map.of()));

        LangfuseSpanScope span = telemetry.startSpan("rag.retrieval", LangfuseObservationType.RETRIEVER,
                Map.of("nexa.retrieval.result_count", 12), trace.context());
        span.close();
        trace.close();

        assertThat(exporter.getFinishedSpanItems()).filteredOn(item -> item.getName().equals("rag.retrieval"))
                .singleElement().satisfies(item -> assertThat(item.getAttributes().asMap())
                        .containsEntry(io.opentelemetry.api.common.AttributeKey.stringKey("langfuse.observation.type"),
                                "retriever")
                        .containsEntry(io.opentelemetry.api.common.AttributeKey.stringKey("langfuse.trace.name"),
                                "rag.chat")
                        .containsEntry(io.opentelemetry.api.common.AttributeKey.stringKey("langfuse.user.id"), "user-001")
                        .containsEntry(io.opentelemetry.api.common.AttributeKey.stringKey("langfuse.session.id"),
                                "conversation-001")
                        .containsEntry(io.opentelemetry.api.common.AttributeKey.stringKey("langfuse.environment"),
                                "local")
                        .containsEntry(io.opentelemetry.api.common.AttributeKey.stringKey("nexa.trace_id"),
                                "business-trace-001"));
    }
}
