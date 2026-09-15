package com.nexarag.infra.observability.langfuse.otel;

import com.nexarag.infra.observability.langfuse.LangfuseTelemetry;
import com.nexarag.infra.observability.langfuse.NoopLangfuseTelemetry;
import com.nexarag.infra.observability.langfuse.aop.LangfuseExpressionResolver;
import com.nexarag.infra.observability.langfuse.config.LangfuseProperties;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.exporter.otlp.http.trace.OtlpHttpSpanExporter;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;

import static com.nexarag.infra.observability.langfuse.constants.LangfuseOtelAttributeConstant.AUTHORIZATION_HEADER;
import static com.nexarag.infra.observability.langfuse.constants.LangfuseOtelAttributeConstant.BASIC_AUTHORIZATION_PREFIX;
import static com.nexarag.infra.observability.langfuse.constants.LangfuseOtelAttributeConstant.DEPLOYMENT_ENVIRONMENT_ATTRIBUTE;
import static com.nexarag.infra.observability.langfuse.constants.LangfuseOtelAttributeConstant.INGESTION_VERSION;
import static com.nexarag.infra.observability.langfuse.constants.LangfuseOtelAttributeConstant.INGESTION_VERSION_HEADER;
import static com.nexarag.infra.observability.langfuse.constants.LangfuseOtelAttributeConstant.OTLP_TRACE_PATH;
import static com.nexarag.infra.observability.langfuse.constants.LangfuseOtelAttributeConstant.OTLP_EXPORTER_TIMEOUT_SECONDS;
import static com.nexarag.infra.observability.langfuse.constants.LangfuseOtelAttributeConstant.OTLP_SCHEDULE_DELAY_MILLIS;
import static com.nexarag.infra.observability.langfuse.constants.LangfuseOtelAttributeConstant.SERVICE_NAME;
import static com.nexarag.infra.observability.langfuse.constants.LangfuseOtelAttributeConstant.SERVICE_NAME_ATTRIBUTE;
import static com.nexarag.infra.observability.langfuse.constants.LangfuseOtelAttributeConstant.TRACER_NAME;

/**
 * Langfuse 的 OpenTelemetry OTLP HTTP 导出配置。
 */
@Configuration(proxyBeanMethods = false)
public class LangfuseOtelConfiguration {

    /**
     * 提供 Langfuse 注解使用的受限 SpEL 解析器。
     *
     * <p>切面始终会被 Spring 扫描；即使 Langfuse 未启用，也必须能完成依赖注入，
     * 由无操作遥测门面负责后续降级。</p>
     *
     * @return 无状态的表达式解析器
     */
    @Bean
    @ConditionalOnMissingBean
    public LangfuseExpressionResolver langfuseExpressionResolver() {
        return new LangfuseExpressionResolver();
    }

    /**
     * 创建专用于 Langfuse 的 Trace Provider，避免覆盖应用其他 OpenTelemetry 配置。
     *
     * @param properties Langfuse 配置
     * @return 可关闭的 Trace Provider
     */
    @Bean(destroyMethod = "close")
    @ConditionalOnProperty(prefix = "nexa.observability.langfuse", name = "enabled", havingValue = "true")
    public SdkTracerProvider langfuseTracerProvider(LangfuseProperties properties) {
        OtlpHttpSpanExporter exporter = OtlpHttpSpanExporter.builder()
                .setEndpoint(traceEndpoint(properties))
                .addHeader(AUTHORIZATION_HEADER, basicAuthorization(properties))
                .addHeader(INGESTION_VERSION_HEADER, INGESTION_VERSION)
                .setTimeout(Duration.ofSeconds(OTLP_EXPORTER_TIMEOUT_SECONDS))
                .build();
        BatchSpanProcessor processor = BatchSpanProcessor.builder(exporter)
                .setScheduleDelay(Duration.ofMillis(OTLP_SCHEDULE_DELAY_MILLIS))
                .setExporterTimeout(Duration.ofSeconds(OTLP_EXPORTER_TIMEOUT_SECONDS))
                .build();
        Resource resource = Resource.getDefault().merge(Resource.create(Attributes.of(
                AttributeKey.stringKey(SERVICE_NAME_ATTRIBUTE), SERVICE_NAME,
                AttributeKey.stringKey(DEPLOYMENT_ENVIRONMENT_ATTRIBUTE), properties.getEnvironment())));
        return SdkTracerProvider.builder()
                .setResource(resource)
                .addSpanProcessor(processor)
                .build();
    }

    /**
     * 在启用时装配真实遥测门面。
     *
     * @param tracerProvider Langfuse 专用 Trace Provider
     * @param properties     Langfuse 配置
     * @return 真实遥测门面
     */
    @Bean
    @ConditionalOnProperty(prefix = "nexa.observability.langfuse", name = "enabled", havingValue = "true")
    public LangfuseTelemetry langfuseTelemetry(SdkTracerProvider tracerProvider, LangfuseProperties properties) {
        return new OpenTelemetryLangfuseTelemetry(tracerProvider.get(TRACER_NAME), properties.getEnvironment());
    }

    /**
     * 在未启用时提供无操作门面，确保业务调用不需要分支判断。
     *
     * @return 无操作遥测门面
     */
    @Bean
    @ConditionalOnMissingBean(LangfuseTelemetry.class)
    public LangfuseTelemetry noopLangfuseTelemetry() {
        return NoopLangfuseTelemetry.INSTANCE;
    }

    private String traceEndpoint(LangfuseProperties properties) {
        String host = properties.getHost().toString();
        return host.endsWith("/") ? host.substring(0, host.length() - 1) + OTLP_TRACE_PATH
                : host + OTLP_TRACE_PATH;
    }

    private String basicAuthorization(LangfuseProperties properties) {
        String raw = properties.getPublicKey() + ":" + properties.getSecretKey();
        return BASIC_AUTHORIZATION_PREFIX + Base64.getEncoder().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }
}
