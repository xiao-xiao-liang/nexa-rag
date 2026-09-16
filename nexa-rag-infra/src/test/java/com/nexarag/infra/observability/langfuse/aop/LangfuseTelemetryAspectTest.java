package com.nexarag.infra.observability.langfuse.aop;

import com.nexarag.infra.observability.langfuse.LangfuseTelemetry;
import com.nexarag.infra.observability.langfuse.LangfuseSpanScope;
import com.nexarag.infra.observability.langfuse.LangfuseTraceScope;
import com.nexarag.infra.observability.langfuse.model.LangfuseObservationType;
import com.nexarag.infra.observability.langfuse.model.LangfuseTraceCommand;
import io.opentelemetry.context.Context;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Langfuse 遥测切面测试。
 */
class LangfuseTelemetryAspectTest {

    @Test
    void shouldResolveRequestFieldsWhenColdFluxIsSubscribed() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(TestConfiguration.class)) {
            RecordingTelemetry telemetry = context.getBean(RecordingTelemetry.class);
            TestTraceService service = context.getBean(TestTraceService.class);

            service.stream(new TraceRequest("trace-001", "conversation-001", "user-001", "generation-001"))
                    .blockLast();

            assertThat(telemetry.commands).singleElement().satisfies(command -> {
                assertThat(command.name()).isEqualTo("rag.chat");
                assertThat(command.correlationId()).isEqualTo("trace-001");
                assertThat(command.sessionId()).isEqualTo("conversation-001");
                assertThat(command.userId()).isEqualTo("user-001");
                assertThat(command.attributes()).containsEntry("nexa.generation_id", "generation-001");
            });
            assertThat(telemetry.closedCount.get()).isEqualTo(1);
        }
    }

    @Test
    void shouldCreateIndependentTraceForEachColdFluxSubscription() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(TestConfiguration.class)) {
            RecordingTelemetry telemetry = context.getBean(RecordingTelemetry.class);
            Flux<String> stream = context.getBean(TestTraceService.class)
                    .stream(new TraceRequest("trace-001", "conversation-001", "user-001", "generation-001"));

            stream.blockLast();
            stream.blockLast();

            assertThat(telemetry.commands).hasSize(2);
            assertThat(telemetry.closedCount.get()).isEqualTo(2);
        }
    }

    @Test
    void shouldMarkTraceAsFailedWhenReactiveStreamFails() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(TestConfiguration.class)) {
            RecordingTelemetry telemetry = context.getBean(RecordingTelemetry.class);

            assertThatThrownBy(() -> context.getBean(TestTraceService.class)
                    .failingStream(new TraceRequest("trace-001", "conversation-001", "user-001", "generation-001"))
                    .blockLast())
                    .isInstanceOf(IllegalStateException.class);

            assertThat(telemetry.failedCount.get()).isEqualTo(1);
            assertThat(telemetry.closedCount.get()).isEqualTo(1);
        }
    }

    @Test
    void shouldConvertNumericTraceIdentityToString() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(TestConfiguration.class)) {
            RecordingTelemetry telemetry = context.getBean(RecordingTelemetry.class);

            context.getBean(TestTraceService.class)
                    .numericUserStream(new NumericTraceRequest("trace-001", "conversation-001", 10001L, "generation-001"))
                    .blockLast();

            assertThat(telemetry.commands).singleElement()
                    .extracting(LangfuseTraceCommand::userId)
                    .isEqualTo("10001");
        }
    }

    @Test
    void shouldRecordSynchronousSpanAttributesAndResultAttributes() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(TestConfiguration.class)) {
            RecordingTelemetry telemetry = context.getBean(RecordingTelemetry.class);

            context.getBean(TestTraceService.class).selectEvidence(new SpanRequest("", 3));

            assertThat(telemetry.spanCommands).singleElement().satisfies(command -> {
                assertThat(command.name()).isEqualTo("rag.evidence-selection");
                assertThat(command.type()).isEqualTo(LangfuseObservationType.EVALUATOR);
                assertThat(command.attributes()).containsEntry("nexa.evidence.candidate_count", 3);
            });
            assertThat(telemetry.spanResultAttributes).containsExactly(Map.of("nexa.evidence.accepted_count", 2));
            assertThat(telemetry.spanClosedCount.get()).isEqualTo(1);
        }
    }

    @Test
    void shouldMarkSynchronousSpanAsFailedWhenMethodThrows() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(TestConfiguration.class)) {
            RecordingTelemetry telemetry = context.getBean(RecordingTelemetry.class);

            assertThatThrownBy(() -> context.getBean(TestTraceService.class).failingSpan(new SpanRequest("", 3)))
                    .isInstanceOf(IllegalStateException.class);

            assertThat(telemetry.spanFailedCount.get()).isEqualTo(1);
            assertThat(telemetry.spanClosedCount.get()).isEqualTo(1);
        }
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAspectJAutoProxy
    static class TestConfiguration {

        @Bean
        RecordingTelemetry recordingTelemetry() {
            return new RecordingTelemetry();
        }

        @Bean
        LangfuseTelemetryAspect langfuseTelemetryAspect(RecordingTelemetry telemetry) {
            return new LangfuseTelemetryAspect(telemetry, new LangfuseExpressionResolver());
        }

        @Bean
        LangfuseSpanAspect langfuseSpanAspect(RecordingTelemetry telemetry) {
            return new LangfuseSpanAspect(telemetry, new LangfuseExpressionResolver());
        }

        @Bean
        TestTraceService testTraceService() {
            return new TestTraceService();
        }
    }

    static class TestTraceService {

        @LangfuseTrace(name = "rag.chat", correlationId = "#request.traceId", sessionId = "#request.conversationId",
                userId = "#request.userId", attributes = "nexa.generation_id=#request.generationId")
        public Flux<String> stream(TraceRequest request) {
            return Flux.just("ok");
        }

        @LangfuseTrace(name = "rag.chat", correlationId = "#request.traceId", sessionId = "#request.conversationId",
                userId = "#request.userId")
        public Flux<String> failingStream(TraceRequest request) {
            return Flux.error(new IllegalStateException("模拟流异常"));
        }

        @LangfuseTrace(name = "rag.chat", correlationId = "#request.traceId", sessionId = "#request.conversationId",
                userId = "#request.userId")
        public Flux<String> numericUserStream(NumericTraceRequest request) {
            return Flux.just("ok");
        }

        @LangfuseSpan(name = "rag.evidence-selection", type = LangfuseObservationType.EVALUATOR,
                parentContextCarrier = "#request.carrier", attributes = "nexa.evidence.candidate_count=#request.count",
                resultAttributes = "nexa.evidence.accepted_count=#result['acceptedCount']")
        public Map<String, Object> selectEvidence(SpanRequest request) {
            return Map.of("acceptedCount", 2);
        }

        @LangfuseSpan(name = "rag.evidence-selection", type = LangfuseObservationType.EVALUATOR,
                parentContextCarrier = "#request.carrier")
        public void failingSpan(SpanRequest request) {
            throw new IllegalStateException("模拟节点异常");
        }
    }

    record TraceRequest(String traceId, String conversationId, String userId, String generationId) {
    }

    record NumericTraceRequest(String traceId, String conversationId, Long userId, String generationId) {
    }

    record SpanRequest(String carrier, int count) {
    }

    static class RecordingTelemetry implements LangfuseTelemetry {

        private final List<LangfuseTraceCommand> commands = new ArrayList<>();
        private final AtomicInteger closedCount = new AtomicInteger();
        private final AtomicInteger failedCount = new AtomicInteger();
        private final List<SpanCommand> spanCommands = new ArrayList<>();
        private final List<Map<String, Object>> spanResultAttributes = new ArrayList<>();
        private final AtomicInteger spanClosedCount = new AtomicInteger();
        private final AtomicInteger spanFailedCount = new AtomicInteger();

        @Override
        public LangfuseTraceScope startTrace(LangfuseTraceCommand command) {
            commands.add(command);
            return new LangfuseTraceScope() {
                @Override
                public Context context() {
                    return Context.root();
                }

                @Override
                public void close() {
                    closedCount.incrementAndGet();
                }

                public void fail(Throwable throwable) {
                    failedCount.incrementAndGet();
                }
            };
        }

        @Override
        public LangfuseSpanScope startSpan(String name, LangfuseObservationType type, Map<String, Object> attributes,
                                           Context parentContext) {
            spanCommands.add(new SpanCommand(name, type, attributes));
            return new LangfuseSpanScope() {
                @Override
                public Context context() {
                    return Context.root();
                }

                @Override
                public void addAttributes(Map<String, Object> resultAttributes) {
                    spanResultAttributes.add(resultAttributes);
                }

                @Override
                public void fail(Throwable throwable) {
                    spanFailedCount.incrementAndGet();
                }

                @Override
                public void close() {
                    spanClosedCount.incrementAndGet();
                }
            };
        }
    }

    record SpanCommand(String name, LangfuseObservationType type, Map<String, Object> attributes) {
    }
}
