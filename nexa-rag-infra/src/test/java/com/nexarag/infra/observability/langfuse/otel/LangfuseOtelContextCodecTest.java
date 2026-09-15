package com.nexarag.infra.observability.langfuse.otel;

import com.nexarag.infra.observability.langfuse.model.LangfuseTraceContextAttributes;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.TraceFlags;
import io.opentelemetry.api.trace.TraceState;
import io.opentelemetry.context.Context;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Langfuse OTel 上下文载体编解码测试。
 */
class LangfuseOtelContextCodecTest {

    @Test
    void shouldRestoreTraceAndParentSpanFromSerializableCarrier() {
        Context source = LangfuseTraceContextAttributes.attach(Context.root().with(Span.wrap(SpanContext.create(
                "0123456789abcdef0123456789abcdef", "0123456789abcdef", TraceFlags.getSampled(),
                TraceState.getDefault()))), new LangfuseTraceContextAttributes("rag.chat", "business-trace-001",
                "conversation-001", "user-001"));

        Context restored = LangfuseOtelContextCodec.decode(LangfuseOtelContextCodec.encode(source));

        assertThat(Span.fromContext(restored).getSpanContext())
                .extracting(SpanContext::getTraceId, SpanContext::getSpanId, SpanContext::isSampled)
                .containsExactly("0123456789abcdef0123456789abcdef", "0123456789abcdef", true);
        assertThat(LangfuseTraceContextAttributes.from(restored))
                .isEqualTo(new LangfuseTraceContextAttributes("rag.chat", "business-trace-001",
                        "conversation-001", "user-001"));
    }

    @Test
    void shouldReturnRootContextForMalformedCarrier() {
        assertThat(Span.fromContext(LangfuseOtelContextCodec.decode("not-a-context"))
                .getSpanContext().isValid())
                .isFalse();
    }
}
