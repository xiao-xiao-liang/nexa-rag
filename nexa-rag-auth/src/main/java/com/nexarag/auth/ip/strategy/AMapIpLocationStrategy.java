package com.nexarag.auth.ip.strategy;

import com.nexarag.auth.config.IpLocationProperties;
import com.nexarag.auth.ip.IpLocation;
import com.nexarag.common.exception.ServiceException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;

/**
 * 使用高德 WebService 高级 IP 定位接口解析市级地区的策略。
 */
@Slf4j
@Component
@ProviderIpLocationStrategy
public class AMapIpLocationStrategy implements IpLocationStrategy {

    private static final String ENDPOINT = "https://restapi.amap.com/v5/ip/location";

    private final String apiKey;
    private final RestClient restClient;

    public AMapIpLocationStrategy(IpLocationProperties properties) {
        this.apiKey = properties.getAmapKey();
        this.restClient = RestClient.builder().baseUrl(ENDPOINT).build();
    }

    @Override
    public String getProvider() {
        return "amap";
    }

    @Override
    public void validateConfiguration() {
        if (apiKey == null || apiKey.isBlank()) {
            throw new ServiceException("高德地图 IP 地区策略缺少 Key 配置");
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public IpLocation locate(String ip) {
        if (!isPublicIpv4(ip)) {
            return IpLocation.UNKNOWN;
        }
        try {
            Map<String, Object> body = restClient.get().uri(uriBuilder -> uriBuilder
                    .queryParam("key", apiKey).queryParam("ip", ip).queryParam("type", 4).build())
                    .retrieve().body(Map.class);
            if (body != null && "1".equals(String.valueOf(body.get("status")))) {
                String city = String.valueOf(body.get("city"));
                return city == null || city.isBlank() || "null".equals(city) ? IpLocation.UNKNOWN : new IpLocation(city);
            }
        } catch (Exception exception) {
            log.warn("高德 IP 地区解析失败，ip={}", maskIp(ip), exception);
        }
        return IpLocation.UNKNOWN;
    }

    private boolean isPublicIpv4(String ip) {
        return ip != null && ip.matches("^(?!10\\.|127\\.|192\\.168\\.|172\\.(1[6-9]|2\\d|3[0-1])\\.)((25[0-5]|2[0-4]\\d|1?\\d?\\d)\\.){3}(25[0-5]|2[0-4]\\d|1?\\d?\\d)$");
    }

    private String maskIp(String ip) {
        return ip == null ? "***" : ip.replaceFirst("\\.\\d+$", ".***");
    }
}
