package com.nexarag.infra.observability.langfuse.model;

/**
 * Langfuse 支持的观察类型。
 *
 * <p>类型值与 Langfuse OTLP v4 的 {@code langfuse.observation.type} 属性保持一致，
 * 业务节点应选择能表达自身职责的最具体类型。</p>
 */
public enum LangfuseObservationType {

    /** 通用耗时步骤。 */
    SPAN("span"),
    /** 离散事件。 */
    EVENT("event"),
    /** 大模型生成调用。 */
    GENERATION("generation"),
    /** 嵌套执行的智能体。 */
    AGENT("agent"),
    /** 外部动作或工具调用。 */
    TOOL("tool"),
    /** 串联多个步骤的处理链。 */
    CHAIN("chain"),
    /** 只读检索步骤。 */
    RETRIEVER("retriever"),
    /** 评估相关性、正确性或质量的步骤。 */
    EVALUATOR("evaluator"),
    /** 向量嵌入调用。 */
    EMBEDDING("embedding"),
    /** 安全防护步骤。 */
    GUARDRAIL("guardrail");

    private final String value;

    LangfuseObservationType(String value) {
        this.value = value;
    }

    /**
     * 返回写入 OpenTelemetry 属性的 Langfuse 类型值。
     *
     * @return Langfuse OTLP 识别的类型值
     */
    public String value() {
        return value;
    }
}
