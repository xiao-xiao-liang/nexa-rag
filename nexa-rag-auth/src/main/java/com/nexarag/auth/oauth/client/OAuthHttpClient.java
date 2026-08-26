package com.nexarag.auth.oauth.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexarag.auth.enums.AuthErrorCode;
import com.nexarag.common.exception.ClientException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Map;
import java.util.function.Consumer;

/**
 * OAuth 外部 HTTP 调用门面：统一超时、JSON 解析和对外错误边界，禁止记录凭据或 Token。
 */
@Component
public class OAuthHttpClient {

    /** 外部授权平台的连接超时。 */
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);

    /** 外部授权平台的响应读取超时。 */
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(10);

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public OAuthHttpClient(RestClient.Builder restClientBuilder, ObjectMapper objectMapper) {
        HttpClient httpClient = HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(READ_TIMEOUT);
        this.restClient = restClientBuilder.requestFactory(requestFactory).build();
        this.objectMapper = objectMapper;
    }

    /**
     * 执行 JSON GET 请求。
     *
     * @param url 目标地址
     * @param headers 自定义请求头
     * @return JSON 响应
     */
    public JsonNode getJson(String url, Consumer<HttpHeaders> headers) {
        try {
            String body = restClient.get()
                    .uri(url)
                    .headers(headers)
                    .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                    .retrieve()
                    .body(String.class);
            return readJson(body);
        } catch (RestClientException exception) {
            throw new ClientException(AuthErrorCode.OAUTH_AUTHORIZATION_FAILED);
        }
    }

    /**
     * 执行 application/x-www-form-urlencoded POST 请求。
     *
     * @param url 目标地址
     * @param form 表单参数
     * @param headers 自定义请求头
     * @return JSON 响应
     */
    public JsonNode postForm(String url, Map<String, String> form, Consumer<HttpHeaders> headers) {
        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        form.forEach(body::add);
        try {
            String response = restClient.post()
                    .uri(url)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .headers(headers)
                    .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                    .body(body)
                    .retrieve()
                    .body(String.class);
            return readJson(response);
        } catch (RestClientException exception) {
            throw new ClientException(AuthErrorCode.OAUTH_AUTHORIZATION_FAILED);
        }
    }

    /**
     * 执行 JSON POST 请求。
     *
     * @param url 目标地址
     * @param payload JSON 请求体
     * @param headers 自定义请求头
     * @return JSON 响应
     */
    public JsonNode postJson(String url, Map<String, String> payload, Consumer<HttpHeaders> headers) {
        try {
            String response = restClient.post()
                    .uri(url)
                    .contentType(MediaType.APPLICATION_JSON)
                    .headers(headers)
                    .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                    .body(payload)
                    .retrieve()
                    .body(String.class);
            return readJson(response);
        } catch (RestClientException exception) {
            throw new ClientException(AuthErrorCode.OAUTH_AUTHORIZATION_FAILED);
        }
    }

    /**
     * 解析响应 JSON，空体、非 JSON 或异常响应一律转换为通用授权失败。
     */
    private JsonNode readJson(String body) {
        if (body == null || body.isBlank()) {
            throw new ClientException(AuthErrorCode.OAUTH_AUTHORIZATION_FAILED);
        }
        try {
            return objectMapper.readTree(body);
        } catch (JsonProcessingException exception) {
            throw new ClientException(AuthErrorCode.OAUTH_AUTHORIZATION_FAILED);
        }
    }
}
