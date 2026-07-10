package com.nexarag.infra.parser.mineru.client;

import com.nexarag.common.error.BaseErrorCode;
import com.nexarag.common.exception.ServiceException;
import com.nexarag.infra.parser.model.MinerUParseCommand;
import com.nexarag.infra.parser.model.MinerUParseResponse;
import com.nexarag.infra.config.MinerUProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 本地部署 MinerU 客户端，负责调用本地 `/file_parse` 接口获取 ZIP 解析产物。
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "nexa.parser.mineru", name = "mode", havingValue = "local", matchIfMissing = true)
public class LocalMinerUClient implements MinerUClient {

    private final MinerUProperties properties;

    /**
     * 调用本地 MinerU 文件解析接口。
     *
     * @param command 解析命令
     * @return MinerU ZIP 解析响应
     */
    @Override
    public MinerUParseResponse parse(MinerUParseCommand command) {
        try {
            // 1. 使用 Spring 标准 multipart 编码构造请求，避免手写边界导致 FastAPI 无法识别文件字段
            HttpEntity<MultiValueMap<String, Object>> requestEntity = buildMultipartRequest(command);

            // 2. 调用本地 MinerU HTTP 接口
            ResponseEntity<byte[]> response = buildRestTemplate().postForEntity(
                    resolveParseUrl(),
                    requestEntity,
                    byte[].class
            );

            // 3. 校验响应并返回 ZIP 流
            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                throw new ServiceException("调用本地MinerU失败，documentId=" + command.documentId()
                        + "，httpStatus=" + response.getStatusCode().value()
                        + "，responseBody=" + truncateResponseBody(response.getBody()), BaseErrorCode.REMOTE_ERROR);
            }
            return MinerUParseResponse.builder()
                    .zipInputStream(new ByteArrayInputStream(response.getBody()))
                    .metadata(Map.of(
                            "clientMode", "local",
                            "httpStatus", response.getStatusCode().value(),
                            "zipSize", response.getBody().length
                    ))
                    .build();
        } catch (HttpStatusCodeException exception) {
            throw new ServiceException("调用本地MinerU失败，documentId=" + command.documentId()
                    + "，httpStatus=" + exception.getStatusCode().value()
                    + "，responseBody=" + truncateResponseBody(exception.getResponseBodyAsByteArray()),
                    exception, BaseErrorCode.REMOTE_ERROR);
        } catch (ServiceException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new ServiceException("调用本地MinerU异常，documentId=" + command.documentId(),
                    exception, BaseErrorCode.REMOTE_ERROR);
        }
    }

    /**
     * 构造本地 MinerU 需要的 multipart/form-data 请求。
     *
     * @param command 解析命令
     * @return multipart 请求实体
     * @throws Exception 读取文件输入流失败时抛出
     */
    private HttpEntity<MultiValueMap<String, Object>> buildMultipartRequest(MinerUParseCommand command) throws Exception {
        MultiValueMap<String, Object> multipartBody = new LinkedMultiValueMap<>();
        multipartBody.add("files", buildFilePart(command));
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("backend", "pipeline");
        fields.put("parse_method", command.enableOcr() ? "ocr" : "auto");
        fields.put("response_format_zip", "true");
        fields.put("return_images", "true");
        fields.put("return_model_output", "false");
        fields.put("return_middle_json", "false");
        for (Map.Entry<String, String> field : fields.entrySet()) {
            multipartBody.add(field.getKey(), field.getValue());
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        headers.setAccept(List.of(MediaType.APPLICATION_OCTET_STREAM, MediaType.APPLICATION_JSON, MediaType.ALL));
        return new HttpEntity<>(multipartBody, headers);
    }

    /**
     * 构造文件表单项，字段名由外层 multipartBody 固定为 files。
     *
     * @param command 解析命令
     * @return 文件表单项
     * @throws Exception 读取文件输入流失败时抛出
     */
    private HttpEntity<ByteArrayResource> buildFilePart(MinerUParseCommand command) throws Exception {
        byte[] fileBytes = command.inputStream().readAllBytes();
        String fileName = sanitizeFileName(command.fileName());
        ByteArrayResource fileResource = new ByteArrayResource(fileBytes) {

            @Override
            public String getFilename() {
                return fileName;
            }
        };

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        return new HttpEntity<>(fileResource, headers);
    }

    private RestTemplate buildRestTemplate() {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(toMillis(resolveConnectTimeout()));
        requestFactory.setReadTimeout(toMillis(resolveReadTimeout()));
        return new RestTemplate(requestFactory);
    }

    private String resolveParseUrl() {
        String endpoint = trimRightSlash(properties.getLocalEndpoint());
        String path = properties.getLocalParsePath();
        if (!StringUtils.hasText(path)) {
            path = "/file_parse";
        }
        if (!path.startsWith("/")) {
            path = "/" + path;
        }
        return endpoint + path;
    }

    private String trimRightSlash(String value) {
        if (!StringUtils.hasText(value)) {
            return "http://127.0.0.1:8000";
        }
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    private String sanitizeFileName(String fileName) {
        if (!StringUtils.hasText(fileName)) {
            return "document";
        }
        return fileName.replace("\\", "_").replace("/", "_").replace("\"", "_");
    }

    private String truncateResponseBody(byte[] responseBody) {
        if (responseBody == null || responseBody.length == 0) {
            return "";
        }
        String body = new String(responseBody, StandardCharsets.UTF_8);
        return body.length() <= 500 ? body : body.substring(0, 500);
    }

    private Duration resolveConnectTimeout() {
        return properties.getConnectTimeout() == null ? Duration.ofSeconds(3) : properties.getConnectTimeout();
    }

    private Duration resolveReadTimeout() {
        return properties.getReadTimeout() == null ? Duration.ofSeconds(120) : properties.getReadTimeout();
    }

    private int toMillis(Duration duration) {
        long millis = duration.toMillis();
        return millis > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) millis;
    }
}
