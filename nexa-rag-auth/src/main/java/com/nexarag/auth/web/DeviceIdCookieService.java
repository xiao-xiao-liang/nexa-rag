package com.nexarag.auth.web;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.security.SecureRandom;
import java.util.Base64;

/** 签发并读取浏览器配置文件级设备标识 Cookie，不将其作为认证凭据。 */
@Service
public class DeviceIdCookieService {
    private static final String COOKIE_NAME = "__Host-nexa-device-id";
    private static final long MAX_AGE_SECONDS = 365L * 24 * 60 * 60;
    private final SecureRandom secureRandom = new SecureRandom();

    public String getOrCreateDeviceId() {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.currentRequestAttributes();
        HttpServletRequest request = attributes.getRequest();
        if (request.getCookies() != null) for (Cookie cookie : request.getCookies()) if (COOKIE_NAME.equals(cookie.getName())) return cookie.getValue();
        byte[] bytes = new byte[32]; secureRandom.nextBytes(bytes);
        String deviceId = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        HttpServletResponse response = attributes.getResponse();
        if (response != null) response.addHeader(HttpHeaders.SET_COOKIE, ResponseCookie.from(COOKIE_NAME, deviceId)
                .path("/").secure(true).httpOnly(true).sameSite("Strict").maxAge(MAX_AGE_SECONDS).build().toString());
        return deviceId;
    }
}
