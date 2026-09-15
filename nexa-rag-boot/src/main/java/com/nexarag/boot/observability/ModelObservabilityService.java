package com.nexarag.boot.observability;

import static com.nexarag.model.constants.ModelTelemetryConstant.COMPLETE_BREAKDOWN_STATUS;
import static com.nexarag.model.constants.ModelTelemetryConstant.PROVIDER_USAGE_INPUT;
import static com.nexarag.model.constants.ModelTelemetryConstant.PROVIDER_USAGE_INPUT_CAMEL_CASE;
import static com.nexarag.model.constants.ModelTelemetryConstant.PROVIDER_USAGE_INPUT_SNAKE_CASE;
import static com.nexarag.model.constants.ModelTelemetryConstant.PROVIDER_USAGE_OUTPUT;
import static com.nexarag.model.constants.ModelTelemetryConstant.PROVIDER_USAGE_OUTPUT_CAMEL_CASE;
import static com.nexarag.model.constants.ModelTelemetryConstant.PROVIDER_USAGE_OUTPUT_SNAKE_CASE;
import static com.nexarag.model.constants.ModelTelemetryConstant.PROVIDER_USAGE_TOTAL;
import static com.nexarag.model.constants.ModelTelemetryConstant.PROVIDER_USAGE_TOTAL_CAMEL_CASE;
import static com.nexarag.model.constants.ModelTelemetryConstant.PROVIDER_USAGE_TOTAL_SNAKE_CASE;
import static com.nexarag.model.constants.ModelTelemetryConstant.RAG_HISTORY_TOKENS;
import static com.nexarag.model.constants.ModelTelemetryConstant.RAG_QUESTION_TOKENS;
import static com.nexarag.model.constants.ModelTelemetryConstant.RAG_RETRIEVAL_TOKENS;
import static com.nexarag.model.constants.ModelTelemetryConstant.RAG_SUMMARY_TOKENS;
import static com.nexarag.model.constants.ModelTelemetryConstant.RAG_SYSTEM_TOKENS;
import static com.nexarag.model.constants.ModelTelemetryConstant.RAG_TEMPLATE_OVERHEAD_TOKENS;
import static com.nexarag.model.constants.ModelTelemetryConstant.RAG_TOKEN_STATUS;
import static com.nexarag.model.constants.ModelTelemetryConstant.RAG_TOOL_TOKENS;
import static com.nexarag.model.constants.ModelTelemetryConstant.ROUTE_KEY;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.nexarag.common.error.BaseErrorCode;
import com.nexarag.common.exception.ClientException;
import com.nexarag.common.exception.ServiceException;
import com.nexarag.infra.observability.langfuse.LangfuseQueryClient;
import com.nexarag.infra.observability.langfuse.api.LangfuseObservationDTO;
import com.nexarag.infra.observability.langfuse.api.LangfuseObservationPageDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * 聚合 Langfuse Generation 观测数据，供管理端判断模型上下文与召回预算。
 */
@Service
@RequiredArgsConstructor
public class ModelObservabilityService {

    private static final Duration DEFAULT_RANGE = Duration.ofHours(24);
    private static final Duration MAX_RANGE = Duration.ofDays(7);
    private static final int DEFAULT_LIMIT = 50;
    private static final int MAX_LIMIT = 200;
    private static final int MAX_PAGE_COUNT = 5;
    private static final Duration CACHE_TTL = Duration.ofSeconds(30);
    private static final long CACHE_MAXIMUM_SIZE = 100;

    private final LangfuseQueryClient queryClient;
    private final Cache<ObservationCacheKey, ObservationFetchResult> observationCache = Caffeine.newBuilder()
            .maximumSize(CACHE_MAXIMUM_SIZE)
            .expireAfterWrite(CACHE_TTL)
            .build();

    /**
     * 返回模型 Token、TTFT 与 Template 开销的安全聚合结果。
     *
     * @param query 查询条件
     * @return 聚合后的展示数据
     */
    public ModelObservabilityOverviewVO overview(ModelObservabilityQuery query) {
        // 1. 固定查询时间和条数边界，避免管理端无界扫描。
        NormalizedQuery normalizedQuery = normalize(query);
        try {
            // 2. 读取已白名单化的 Generation 字段，并执行本地筛选。
            ObservationFetchResult fetchResult = loadObservations(normalizedQuery);
            List<LangfuseObservationDTO> observations = fetchResult.observations().stream()
                    .filter(observation -> matches(observation, normalizedQuery))
                    .limit(normalizedQuery.limit())
                    .toList();

            // 3. 仅使用厂商明确返回 usage 的记录计算精确指标。
            List<Integer> inputTokens = new ArrayList<>();
            List<Integer> outputTokens = new ArrayList<>();
            List<Integer> ttftValues = new ArrayList<>();
            List<Integer> templateOverheadTokens = new ArrayList<>();
            for (LangfuseObservationDTO observation : observations) {
                Integer input = numericValue(observation.usageDetails(), PROVIDER_USAGE_INPUT,
                        PROVIDER_USAGE_INPUT_CAMEL_CASE, PROVIDER_USAGE_INPUT_SNAKE_CASE);
                Integer output = numericValue(observation.usageDetails(), PROVIDER_USAGE_OUTPUT,
                        PROVIDER_USAGE_OUTPUT_CAMEL_CASE, PROVIDER_USAGE_OUTPUT_SNAKE_CASE);
                if (input == null || output == null) {
                    continue;
                }
                inputTokens.add(input);
                outputTokens.add(output);
                if (observation.timeToFirstToken() != null) {
                    ttftValues.add(observation.timeToFirstToken().intValue());
                }
                if (COMPLETE_BREAKDOWN_STATUS.equals(metadataValue(observation.metadata(), RAG_TOKEN_STATUS))) {
                    Integer overhead = numericValue(observation.metadata(), RAG_TEMPLATE_OVERHEAD_TOKENS);
                    if (overhead != null) {
                        templateOverheadTokens.add(overhead);
                    }
                }
            }
            long generationCount = observations.size();
            return new ModelObservabilityOverviewVO(true, null, generationCount, inputTokens.size(),
                    generationCount == 0 ? null : (double) inputTokens.size() / generationCount,
                    percentile(inputTokens, 0.50D), percentile(inputTokens, 0.95D),
                    percentile(outputTokens, 0.50D), percentile(outputTokens, 0.95D),
                    percentile(ttftValues, 0.50D), percentile(ttftValues, 0.95D),
                    percentile(templateOverheadTokens, 0.50D), fetchResult.truncated());
        } catch (ServiceException exception) {
            return ModelObservabilityOverviewVO.unavailable();
        }
    }

    /**
     * 返回完整 RAG 分段的 Token 分布。
     *
     * @param query 查询条件
     * @return 仅包含完整分段样本的分布数据
     */
    public ModelTokenDistributionVO tokenDistribution(ModelObservabilityQuery query) {
        NormalizedQuery normalizedQuery = normalize(query);
        try {
            ObservationFetchResult fetchResult = loadObservations(normalizedQuery);
            List<LangfuseObservationDTO> observations = fetchResult.observations().stream()
                    .filter(observation -> matches(observation, normalizedQuery))
                    .limit(normalizedQuery.limit())
                    .filter(observation -> COMPLETE_BREAKDOWN_STATUS.equals(metadataValue(observation.metadata(),
                            RAG_TOKEN_STATUS)))
                    .toList();
            return new ModelTokenDistributionVO(true, null, observations.size(),
                    percentile(metadataNumbers(observations, RAG_SYSTEM_TOKENS), 0.50D),
                    percentile(metadataNumbers(observations, RAG_SUMMARY_TOKENS), 0.50D),
                    percentile(metadataNumbers(observations, RAG_HISTORY_TOKENS), 0.50D),
                    percentile(metadataNumbers(observations, RAG_QUESTION_TOKENS), 0.50D),
                    percentile(metadataNumbers(observations, RAG_RETRIEVAL_TOKENS), 0.50D),
                    percentile(metadataNumbers(observations, RAG_TOOL_TOKENS), 0.50D),
                    percentile(metadataNumbers(observations, RAG_TEMPLATE_OVERHEAD_TOKENS), 0.50D),
                    fetchResult.truncated());
        } catch (ServiceException exception) {
            return ModelTokenDistributionVO.unavailable();
        }
    }

    /**
     * 查询最近的安全调用明细，用于管理端跳转或定位异常样本。
     *
     * @param query 时间范围和可选筛选条件
     * @return 不含正文和身份信息的调用明细
     */
    public List<ModelObservabilityTraceVO> traces(ModelObservabilityQuery query) {
        NormalizedQuery normalizedQuery = normalize(query);
        try {
            return loadObservations(normalizedQuery).observations().stream()
                    .filter(observation -> matches(observation, normalizedQuery))
                    .limit(normalizedQuery.limit())
                    .map(observation -> new ModelObservabilityTraceVO(observation.traceId(), observation.id(),
                            observation.startTime(), observation.model(),
                            metadataValue(observation.metadata(), ROUTE_KEY), observation.level(),
                            numericValue(observation.usageDetails(), PROVIDER_USAGE_INPUT,
                                    PROVIDER_USAGE_INPUT_CAMEL_CASE, PROVIDER_USAGE_INPUT_SNAKE_CASE),
                            numericValue(observation.usageDetails(), PROVIDER_USAGE_OUTPUT,
                                    PROVIDER_USAGE_OUTPUT_CAMEL_CASE, PROVIDER_USAGE_OUTPUT_SNAKE_CASE),
                            numericValue(observation.usageDetails(), PROVIDER_USAGE_TOTAL,
                                    PROVIDER_USAGE_TOTAL_CAMEL_CASE, PROVIDER_USAGE_TOTAL_SNAKE_CASE),
                            observation.timeToFirstToken() == null ? null : observation.timeToFirstToken().intValue(),
                            metadataValue(observation.metadata(), RAG_TOKEN_STATUS)))
                    .toList();
        } catch (ServiceException exception) {
            return List.of();
        }
    }

    private ObservationFetchResult loadObservations(NormalizedQuery query) {
        ObservationCacheKey cacheKey = new ObservationCacheKey(query.from(), query.to());
        return observationCache.get(cacheKey, ignored -> fetchObservations(query));
    }

    private ObservationFetchResult fetchObservations(NormalizedQuery query) {
        List<LangfuseObservationDTO> observations = new ArrayList<>();
        String cursor = null;
        for (int pageIndex = 0; pageIndex < MAX_PAGE_COUNT; pageIndex++) {
            LangfuseObservationPageDTO page = queryClient.fetchGenerations(query.from(), query.to(), cursor);
            observations.addAll(page.data());
            cursor = page.meta() == null ? null : page.meta().cursor();
            if (!StringUtils.hasText(cursor)) {
                break;
            }
        }
        return new ObservationFetchResult(List.copyOf(observations), StringUtils.hasText(cursor));
    }

    private NormalizedQuery normalize(ModelObservabilityQuery query) {
        ModelObservabilityQuery source = query == null ? new ModelObservabilityQuery(null, null, null, null, null, null)
                : query;
        Instant to = source.to() == null ? cacheBucket(Instant.now()) : source.to();
        Instant from = source.from() == null ? to.minus(DEFAULT_RANGE) : source.from();
        if (!from.isBefore(to) || Duration.between(from, to).compareTo(MAX_RANGE) > 0) {
            throw new ClientException("模型观测查询时间范围必须在 7 天内且起始时间早于结束时间", BaseErrorCode.PARAM_ERROR);
        }
        int limit = source.limit() == null ? DEFAULT_LIMIT : source.limit();
        if (limit < 1 || limit > MAX_LIMIT) {
            throw new ClientException("模型观测查询条数必须在 1 到 200 之间", BaseErrorCode.PARAM_ERROR);
        }
        return new NormalizedQuery(from, to, source.modelName(), source.routeKey(), source.status(), limit);
    }

    private boolean matches(LangfuseObservationDTO observation, NormalizedQuery query) {
        return (!StringUtils.hasText(query.modelName()) || query.modelName().equals(observation.model()))
                && (!StringUtils.hasText(query.routeKey())
                || query.routeKey().equals(metadataValue(observation.metadata(), ROUTE_KEY)))
                && (!StringUtils.hasText(query.status()) || query.status().equals(observation.level()));
    }

    private Integer numericValue(Map<String, Object> values, String... keys) {
        if (values == null) {
            return null;
        }
        for (String key : keys) {
            Object value = values.get(key);
            if (value instanceof Number number) {
                return number.intValue();
            }
        }
        return null;
    }

    private String metadataValue(Map<String, Object> metadata, String key) {
        Object value = metadata == null ? null : metadata.get(key);
        return value == null ? null : String.valueOf(value);
    }

    private List<Integer> metadataNumbers(List<LangfuseObservationDTO> observations, String key) {
        return observations.stream()
                .map(observation -> numericValue(observation.metadata(), key))
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    private Integer percentile(List<Integer> values, double percentile) {
        if (values == null || values.isEmpty()) {
            return null;
        }
        List<Integer> sorted = values.stream().sorted(Comparator.naturalOrder()).toList();
        int index = Math.max(0, (int) Math.ceil(percentile * sorted.size()) - 1);
        return sorted.get(index);
    }

    private Instant cacheBucket(Instant instant) {
        long bucketSeconds = CACHE_TTL.getSeconds();
        return Instant.ofEpochSecond(Math.floorDiv(instant.getEpochSecond(), bucketSeconds) * bucketSeconds);
    }

    private record NormalizedQuery(Instant from, Instant to, String modelName, String routeKey, String status,
                                   int limit) {
    }

    private record ObservationCacheKey(Instant from, Instant to) {
    }

    private record ObservationFetchResult(List<LangfuseObservationDTO> observations, boolean truncated) {
    }
}
