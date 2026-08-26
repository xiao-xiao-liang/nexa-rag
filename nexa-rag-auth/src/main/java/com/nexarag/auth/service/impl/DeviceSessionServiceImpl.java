package com.nexarag.auth.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.nexarag.auth.config.IpLocationProperties;
import com.nexarag.auth.context.UserContext;
import com.nexarag.auth.ip.factory.IpLocationStrategyFactory;
import com.nexarag.auth.ip.strategy.IpLocationStrategy;
import com.nexarag.auth.mapper.DeviceSessionMapper;
import com.nexarag.auth.model.dataobject.DeviceSessionDO;
import com.nexarag.auth.model.vo.DeviceSessionVO;
import com.nexarag.auth.service.DeviceSessionService;
import com.nexarag.auth.service.RecentVerificationService;
import com.nexarag.auth.service.SecurityAuditService;
import com.nexarag.auth.web.DeviceIdCookieService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;

/**
 * 基于 Sa-Token 原始 Token 哈希追踪并撤销设备会话的实现。
 */
@Service
@RequiredArgsConstructor
public class DeviceSessionServiceImpl implements DeviceSessionService {

    private final DeviceSessionMapper mapper;
    private final IpLocationProperties properties;
    private final IpLocationStrategyFactory strategyFactory;
    private final SecurityAuditService securityAuditService;
    private final DeviceIdCookieService deviceIdCookieService;
    private final RecentVerificationService recentVerificationService;

    @Override
    public void recordCurrentLogin(Long userId) {
        String token = StpUtil.getTokenValue();
        if (token == null)
            return;

        String ip = request().getHeader("X-Real-IP");
        if (ip == null || ip.isBlank())
            ip = request().getRemoteAddr();

        LocalDateTime now = LocalDateTime.now();
        String hash = sha256(token);
        IpLocationStrategy ipLocationStrategy = strategyFactory.create(properties.getProvider());
        mapper.insert(new DeviceSessionDO(IdWorker.getId(), userId, hash, sha256(deviceIdCookieService.getOrCreateDeviceId()),
                deviceName(request().getHeader("User-Agent")), null, maskIp(ip), ipLocationStrategy.locate(ip).city(), now, now, null));
    }

    @Override
    public void touchCurrentSession() {
        String token = StpUtil.getTokenValue();
        if (token != null)
            mapper.touchActiveByTokenHash(sha256(token), LocalDateTime.now());
    }

    @Override
    public List<DeviceSessionVO> listCurrentUserSessions() {
        Long userId = Long.valueOf(UserContext.getUserId());
        return mapper.selectActiveByUserId(userId).stream().map(d -> new DeviceSessionVO(d.getDeviceSessionId(), d.getDeviceIdHash().substring(0, Math.min(12, d.getDeviceIdHash().length())), d.getDeviceName(), d.getDeviceLabel(), d.getMaskedIp(), d.getCity(), d.getLoginTime(), d.getLastActiveTime())).toList();
    }

    @Override
    public void kickoutCurrentUserSession(Long id) {
        recentVerificationService.requireCurrentSessionGrant();
        Long userId = Long.valueOf(UserContext.getUserId());
        DeviceSessionDO d = mapper.selectActiveByIdAndUserIdForUpdate(id, userId);
        if (d == null) return;
        StpUtil.getTokenValueListByLoginId(userId).stream().filter(t -> sha256(t).equals(d.getSaTokenSessionKeyHash())).forEach(StpUtil::kickoutByTokenValue);
        d.setRevokedTime(LocalDateTime.now());
        mapper.updateById(d);
        securityAuditService.recordSuccess(userId, "DEVICE_KICKOUT", "指定设备下线");
    }

    @Override
    public void logoutAllCurrentUserSessions() {
        recentVerificationService.requireCurrentSessionGrant();
        Long userId = Long.valueOf(UserContext.getUserId());
        StpUtil.logout(userId);
        mapper.revokeActiveByUserId(userId, LocalDateTime.now());
        securityAuditService.recordSuccess(userId, "DEVICE_LOGOUT_ALL", "已退出全部登录设备");
    }

    @Override
    public void logoutCurrentSession() {
        if (!StpUtil.isLogin()) {
            return;
        }
        Long userId = Long.valueOf(UserContext.getUserId());
        String token = StpUtil.getTokenValue();
        if (token != null) {
            mapper.revokeActiveByTokenHash(sha256(token), LocalDateTime.now());
        }
        StpUtil.logout();
        securityAuditService.recordSuccess(userId, "LOGOUT", "已退出当前设备登录");
    }

    private HttpServletRequest request() {
        return ((ServletRequestAttributes) RequestContextHolder.currentRequestAttributes()).getRequest();
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private String maskIp(String ip) {
        return ip != null && ip.contains(".") ? ip.replaceFirst("\\.\\d+$", ".***") : "***";
    }

    private String deviceName(String ua) {
        return ua == null || ua.isBlank() ? "未知设备" : ua.substring(0, Math.min(256, ua.length()));
    }
}
