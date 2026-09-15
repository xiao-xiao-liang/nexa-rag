package com.nexarag.infra.observability.langfuse.otel;

import com.nexarag.infra.observability.langfuse.model.LangfuseTraceContextAttributes;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.TraceFlags;
import io.opentelemetry.api.trace.TraceState;
import io.opentelemetry.context.Context;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static com.nexarag.infra.observability.langfuse.constants.LangfuseContextConstant.CARRIER_DELIMITER;
import static com.nexarag.infra.observability.langfuse.constants.LangfuseContextConstant.CARRIER_PART_COUNT;
import static com.nexarag.infra.observability.langfuse.constants.LangfuseContextConstant.CARRIER_SPLIT_PATTERN;

/**
 * 在可序列化工作流状态与 OTel 父上下文之间转换的工具。
 *
 * <p>Graph State 只能保存纯字符串，不能持有 {@link Context} 或 {@link Span} 等包含 SDK 运行时资源的对象。</p>
 */
public final class LangfuseOtelContextCodec {

    private LangfuseOtelContextCodec() {
    }

    /**
     * 提取当前 OTel 上下文的 Trace 与 Span 标识，生成可安全存入 Graph State 的字符串载体。
     *
     * @param context OTel 上下文
     * @return 可序列化上下文载体；不存在有效 Span 时返回空字符串
     */
    public static String encode(Context context) {
        SpanContext spanContext = Span.fromContext(context == null ? Context.root() : context).getSpanContext();
        if (!spanContext.isValid()) {
            return "";
        }
        LangfuseTraceContextAttributes attributes = LangfuseTraceContextAttributes.from(context);
        return String.join(CARRIER_DELIMITER,
                encodePart(spanContext.getTraceId()),
                encodePart(spanContext.getSpanId()),
                encodePart(attributes == null ? null : attributes.traceName()),
                encodePart(attributes == null ? null : attributes.correlationId()),
                encodePart(attributes == null ? null : attributes.sessionId()),
                encodePart(attributes == null ? null : attributes.userId()));
    }

    /**
     * 从 Graph State 中的字符串载体重建父上下文。
     *
     * <p>仅重建 Trace/Span 标识，不恢复或传播任何 exporter、TLS、用户内容等运行时对象。</p>
     *
     * @param carrier 可序列化上下文载体
     * @return 可作为新 Span 父节点的 OTel 上下文；非法输入返回根上下文
     */
    public static Context decode(String carrier) {
        if (!StringUtils.hasText(carrier)) {
            return Context.root();
        }
        String[] parts = carrier.split(CARRIER_SPLIT_PATTERN, -1);
        if (parts.length != CARRIER_PART_COUNT) {
            return Context.root();
        }
        try {
            SpanContext spanContext = SpanContext.createFromRemoteParent(decodePart(parts[0]), decodePart(parts[1]),
                    TraceFlags.getSampled(), TraceState.getDefault());
            if (!spanContext.isValid()) {
                return Context.root();
            }
            LangfuseTraceContextAttributes attributes = new LangfuseTraceContextAttributes(decodePart(parts[2]),
                    decodePart(parts[3]), decodePart(parts[4]), decodePart(parts[5]));
            return LangfuseTraceContextAttributes.attach(Context.root().with(Span.wrap(spanContext)), attributes);
        } catch (IllegalArgumentException exception) {
            return Context.root();
        }
    }

    private static String encodePart(String value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(
                (value == null ? "" : value).getBytes(StandardCharsets.UTF_8));
    }

    private static String decodePart(String value) {
        return new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8);
    }
}
