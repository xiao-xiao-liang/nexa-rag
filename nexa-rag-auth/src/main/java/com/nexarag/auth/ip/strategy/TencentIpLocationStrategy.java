package com.nexarag.auth.ip.strategy;

import com.nexarag.auth.config.IpLocationProperties;
import com.nexarag.auth.ip.IpLocation;
import com.nexarag.common.exception.ServiceException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;

/**
 * 使用腾讯位置服务 IP 定位接口解析市级地区的策略。
 */
@Slf4j
@Component
public class TencentIpLocationStrategy implements IpLocationStrategy {

    private static final String ENDPOINT = "https://apis.map.qq.com/ws/location/v1/ip";

    private static final String REQUEST_PATH = "/ws/location/v1/ip";

    private final String apiKey;
    private final String signKey;
    private final RestClient restClient;

    public TencentIpLocationStrategy(IpLocationProperties properties) {
        this.apiKey = properties.getTencentKey();
        this.signKey = properties.getTencentSk();
        this.restClient = RestClient.builder().baseUrl(ENDPOINT).build();
    }

    @Override
    public String getProvider() {
        return "tencent";
    }

    @Override
    public void validateConfiguration() {
        requireConfigured(apiKey, "腾讯地图");
        requireConfigured(signKey, "腾讯地图签名");
    }

    @Override
    @SuppressWarnings("unchecked")
    public IpLocation locate(String ip) {
        if (ip == null || ip.isBlank()) {
            return IpLocation.UNKNOWN;
        }
        try {
            Map<String, Object> body = restClient.get().uri(uriBuilder -> uriBuilder
                    .queryParam("key", apiKey).queryParam("ip", ip).queryParam("sig", sign(ip)).build())
                    .retrieve().body(Map.class);
            if (body == null || !"0".equals(String.valueOf(body.get("status")))) {
                return IpLocation.UNKNOWN;
            }
            Object result = body.get("result");
            if (!(result instanceof Map<?, ?> resultMap) || !(resultMap.get("ad_info") instanceof Map<?, ?> adInfo)) {
                return IpLocation.UNKNOWN;
            }
            Object city = adInfo.get("city");
            return city instanceof String value && !value.isBlank() ? new IpLocation(value) : IpLocation.UNKNOWN;
        } catch (Exception exception) {
            log.warn("腾讯 IP 地区解析失败，ip={}", maskIp(ip), exception);
            return IpLocation.UNKNOWN;
        }
    }

    private String maskIp(String ip) {
        return ip.contains(":") ? "***" : ip.replaceFirst("\\.\\d+$", ".***");
    }

    /**
     * 按腾讯 WebService 的请求路径、字典序参数与 SK 生成 MD5 签名。
     */
    private String sign(String ip) {
        String parameterString = "ip=" + encode(ip) + "&key=" + encode(apiKey);
        String content = REQUEST_PATH + '?' + parameterString + signKey;
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("MD5")
                    .digest(content.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("JVM 未提供 MD5 签名算法", exception);
        }
    }

    /**
     * 编码参数值，确保参与签名的文本与实际 GET 查询参数保持一致。
     */
    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private void requireConfigured(String value, String providerName) {
        if (value == null || value.isBlank()) {
            throw new ServiceException(providerName + " IP 地区策略缺少 Key 配置");
        }
    }
}
