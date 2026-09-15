package com.nexarag.infra.observability.langfuse;

import com.nexarag.common.error.BaseErrorCode;
import com.nexarag.common.exception.ServiceException;
import com.nexarag.infra.observability.langfuse.api.LangfuseObservationPageDTO;
import com.nexarag.infra.observability.langfuse.config.LangfuseProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.time.Instant;

import static com.nexarag.infra.observability.langfuse.constants.LangfuseApiConstant.OBSERVATION_PAGE_LIMIT;
import static com.nexarag.infra.observability.langfuse.constants.LangfuseApiConstant.OBSERVATIONS_PATH;
import static com.nexarag.infra.observability.langfuse.constants.LangfuseApiConstant.OBSERVATION_FIELDS;
import static com.nexarag.infra.observability.langfuse.constants.LangfuseApiConstant.OBSERVATION_TYPE_GENERATION;
import static com.nexarag.infra.observability.langfuse.constants.LangfuseApiConstant.QUERY_CURSOR;
import static com.nexarag.infra.observability.langfuse.constants.LangfuseApiConstant.QUERY_CONNECT_TIMEOUT_SECONDS;
import static com.nexarag.infra.observability.langfuse.constants.LangfuseApiConstant.QUERY_FIELDS;
import static com.nexarag.infra.observability.langfuse.constants.LangfuseApiConstant.QUERY_FROM_START_TIME;
import static com.nexarag.infra.observability.langfuse.constants.LangfuseApiConstant.QUERY_LIMIT;
import static com.nexarag.infra.observability.langfuse.constants.LangfuseApiConstant.QUERY_READ_TIMEOUT_SECONDS;
import static com.nexarag.infra.observability.langfuse.constants.LangfuseApiConstant.QUERY_TO_START_TIME;
import static com.nexarag.infra.observability.langfuse.constants.LangfuseApiConstant.QUERY_TYPE;

/**
 * 受限访问 Langfuse v4 查询接口的服务端客户端。
 */
@Component
@Slf4j
public class LangfuseQueryClient {

    private final RestClient restClient;
    private final LangfuseProperties properties;

    public LangfuseQueryClient(RestClient.Builder restClientBuilder, LangfuseProperties properties) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(QUERY_CONNECT_TIMEOUT_SECONDS)).build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofSeconds(QUERY_READ_TIMEOUT_SECONDS));
        this.restClient = restClientBuilder.requestFactory(requestFactory).build();
        this.properties = properties;
    }

    /**
     * 查询指定时间范围内的一页 Generation 记录。
     *
     * @param fromStartTime 起始时间（含）
     * @param toStartTime   结束时间（不含）
     * @param cursor        上一页返回的游标，可为空
     * @return 不含输入输出正文的观测分页结果
     */
    public LangfuseObservationPageDTO fetchGenerations(Instant fromStartTime, Instant toStartTime, String cursor) {
        // 1. 禁用状态下不发起网络调用。
        if (!properties.isEnabled()) {
            throw new ServiceException("Langfuse 查询未启用", BaseErrorCode.REMOTE_ERROR);
        }
        try {
            // 2. 固定字段白名单和分页上限，避免正文和无界扫描进入后端。
            URI uri = UriComponentsBuilder.fromUri(properties.getHost())
                    .path(OBSERVATIONS_PATH)
                    .queryParam(QUERY_FROM_START_TIME, fromStartTime)
                    .queryParam(QUERY_TO_START_TIME, toStartTime)
                    .queryParam(QUERY_TYPE, OBSERVATION_TYPE_GENERATION)
                    .queryParam(QUERY_FIELDS, OBSERVATION_FIELDS)
                    .queryParam(QUERY_LIMIT, OBSERVATION_PAGE_LIMIT)
                    .queryParamIfPresent(QUERY_CURSOR, java.util.Optional.ofNullable(cursor))
                    .build()
                    .encode()
                    .toUri();
            LangfuseObservationPageDTO response = restClient.get()
                    .uri(uri)
                    .accept(MediaType.APPLICATION_JSON)
                    .headers(headers -> headers.setBasicAuth(properties.getPublicKey(), properties.getSecretKey()))
                    .retrieve()
                    .body(LangfuseObservationPageDTO.class);
            if (response == null) {
                throw new ServiceException("Langfuse 查询未返回有效响应", BaseErrorCode.REMOTE_ERROR);
            }
            return response;
        } catch (ServiceException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            log.warn("Langfuse 查询不可用，异常类型={}", exception.getClass().getSimpleName());
            throw new ServiceException("Langfuse 查询暂时不可用", exception, BaseErrorCode.REMOTE_ERROR);
        }
    }
}
