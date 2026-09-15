package com.nexarag.infra.observability.langfuse.constants;

/**
 * Langfuse 注解表达式的受限属性前缀和敏感字段标记。
 */
public final class LangfuseExpressionConstant {

    public static final String NEXA_ATTRIBUTE_PREFIX = "nexa.";
    public static final String GEN_AI_ATTRIBUTE_PREFIX = "gen_ai.";
    public static final String LANGFUSE_ATTRIBUTE_PREFIX = "langfuse.";
    public static final String INPUT_FIELD = "input";
    public static final String OUTPUT_FIELD = "output";
    public static final String PROMPT_FIELD = "prompt";

    private LangfuseExpressionConstant() {
    }
}
