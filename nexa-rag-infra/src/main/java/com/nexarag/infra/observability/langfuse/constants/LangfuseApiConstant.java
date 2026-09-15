package com.nexarag.infra.observability.langfuse.constants;

/**
 * Langfuse Observations 查询 API 的路径、参数和固定值。
 */
public final class LangfuseApiConstant {

    public static final int OBSERVATION_PAGE_LIMIT = 200;
    public static final long QUERY_CONNECT_TIMEOUT_SECONDS = 3L;
    public static final long QUERY_READ_TIMEOUT_SECONDS = 5L;
    public static final String OBSERVATIONS_PATH = "/api/public/v2/observations";
    public static final String OBSERVATION_FIELDS = "core,basic,usage,metrics,metadata,model";
    public static final String QUERY_FROM_START_TIME = "fromStartTime";
    public static final String QUERY_TO_START_TIME = "toStartTime";
    public static final String QUERY_TYPE = "type";
    public static final String QUERY_FIELDS = "fields";
    public static final String QUERY_LIMIT = "limit";
    public static final String QUERY_CURSOR = "cursor";
    public static final String OBSERVATION_TYPE_GENERATION = "GENERATION";

    private LangfuseApiConstant() {
    }
}
