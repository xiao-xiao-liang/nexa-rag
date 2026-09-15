package com.nexarag.infra.observability.langfuse.constants;

/**
 * Langfuse 与 OpenTelemetry 交互时使用的协议属性键和值。
 */
public final class LangfuseOtelAttributeConstant {

    public static final long OTLP_EXPORTER_TIMEOUT_SECONDS = 3L;
    public static final long OTLP_SCHEDULE_DELAY_MILLIS = 200L;
    public static final String TRACE_NAME = "langfuse.trace.name";
    public static final String USER_ID = "langfuse.user.id";
    public static final String SESSION_ID = "langfuse.session.id";
    public static final String ENVIRONMENT = "langfuse.environment";
    public static final String OBSERVATION_TYPE = "langfuse.observation.type";
    public static final String TRACE_ID_ATTRIBUTE = "nexa.trace_id";
    public static final String GENERATION_ID_ATTRIBUTE = "nexa.generation_id";
    public static final String TENANT_ID_ATTRIBUTE = "nexa.tenant_id";
    public static final String MODEL_CALL_ID_ATTRIBUTE = "nexa.model_call_id";
    public static final String TERMINAL_STATE_ATTRIBUTE = "nexa.terminal_state";
    public static final String OPENINFERENCE_SPAN_KIND = "openinference.span.kind";
    public static final String OPENINFERENCE_LLM = "LLM";
    public static final String GEN_AI_OPERATION_NAME = "gen_ai.operation.name";
    public static final String GEN_AI_CHAT_OPERATION = "chat";
    public static final String GEN_AI_REQUEST_MODEL = "gen_ai.request.model";
    public static final String GEN_AI_PROVIDER_NAME = "gen_ai.provider.name";
    public static final String GEN_AI_INPUT_TOKENS = "gen_ai.usage.input_tokens";
    public static final String GEN_AI_OUTPUT_TOKENS = "gen_ai.usage.output_tokens";
    public static final String GEN_AI_TOTAL_TOKENS = "gen_ai.usage.total_tokens";
    public static final String GEN_AI_FIRST_TOKEN_LATENCY_MS = "gen_ai.response.first_token_latency_ms";
    public static final String UNKNOWN_EXCEPTION = "UnknownException";
    public static final String COMPLETED_STATE = "COMPLETED";
    public static final String CANCELED_STATE = "CANCELED";
    public static final String ERROR_STATE = "ERROR";
    public static final String OTLP_TRACE_PATH = "/api/public/otel/v1/traces";
    public static final String AUTHORIZATION_HEADER = "Authorization";
    public static final String BASIC_AUTHORIZATION_PREFIX = "Basic ";
    public static final String INGESTION_VERSION_HEADER = "x-langfuse-ingestion-version";
    public static final String INGESTION_VERSION = "4";
    public static final String SERVICE_NAME_ATTRIBUTE = "service.name";
    public static final String DEPLOYMENT_ENVIRONMENT_ATTRIBUTE = "deployment.environment.name";
    public static final String SERVICE_NAME = "nexa-rag";
    public static final String TRACER_NAME = "nexa-rag-langfuse";

    private LangfuseOtelAttributeConstant() {
    }
}
