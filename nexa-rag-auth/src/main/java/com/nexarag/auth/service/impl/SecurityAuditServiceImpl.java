package com.nexarag.auth.service.impl;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.nexarag.auth.config.IpLocationProperties;
import com.nexarag.auth.context.UserContext;
import com.nexarag.auth.ip.IpLocation;
import com.nexarag.auth.ip.factory.IpLocationStrategyFactory;
import com.nexarag.auth.ip.strategy.IpLocationStrategy;
import com.nexarag.auth.mapper.SecurityAuditEventMapper;
import com.nexarag.auth.model.dataobject.SecurityAuditEventDO;
import com.nexarag.auth.model.vo.SecurityAuditEventVO;
import com.nexarag.auth.service.SecurityAuditService;
import com.nexarag.auth.service.SecurityNotificationService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 安全审计实现，保存不含密码、验证码和 Token 的事件摘要、原始客户端 IP 及市级地区。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SecurityAuditServiceImpl implements SecurityAuditService {

    private final SecurityAuditEventMapper mapper;
    private final SecurityNotificationService notificationService;
    private final IpLocationProperties ipLocationProperties;
    private final IpLocationStrategyFactory ipLocationStrategyFactory;

    /**
     * {@inheritDoc}
     */
    @Override
    public void recordSuccess(Long userId, String eventType, String detailSummary) {
        // 1. 从当前 HTTP 请求提取原始 IP 和市级地区；异步或非 Web 调用安全降级
        ClientLocation clientLocation = resolveCurrentClientLocation();

        // 2. 持久化不包含认证凭据的安全事件
        mapper.insert(new SecurityAuditEventDO(IdWorker.getId(), userId, userId, eventType, 0, null,
                clientLocation.clientIp(), clientLocation.city(), detailSummary, LocalDateTime.now()));

        // 3. 发送用户侧安全通知
        notificationService.notifyUser(userId, detailSummary);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<SecurityAuditEventVO> listCurrentUserEvents() {
        Long userId = Long.valueOf(UserContext.getUserId());
        return mapper.selectByUserIdSince(userId, LocalDateTime.now().minusDays(90)).stream()
                .map(event -> new SecurityAuditEventVO(event.getEventId(), event.getEventType(), event.getEventResult(),
                        event.getMaskedIp(), event.getCity(), event.getDetailSummary(), event.getCreateTime()))
                .toList();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int cleanupExpiredEvents() {
        return mapper.deleteBefore(LocalDateTime.now().minusDays(180));
    }

    /**
     * 从当前请求解析审计所需的原始客户端地址和地区。
     */
    private ClientLocation resolveCurrentClientLocation() {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes)) {
            return ClientLocation.UNKNOWN;
        }
        String clientIp = resolveClientIp(attributes.getRequest());
        if (clientIp == null || clientIp.isBlank()) {
            return ClientLocation.UNKNOWN;
        }
        try {
            IpLocationStrategy strategy = ipLocationStrategyFactory.create(ipLocationProperties.getProvider());
            IpLocation location = strategy.locate(clientIp);
            String city = location == null || location.city() == null || location.city().isBlank()
                    ? IpLocation.UNKNOWN.city() : location.city();
            return new ClientLocation(clientIp, city);
        } catch (RuntimeException exception) {
            log.warn("安全审计 IP 地区解析失败", exception);
            return new ClientLocation(clientIp, IpLocation.UNKNOWN.city());
        }
    }

    /**
     * 读取反向代理传递的客户端 IP；未提供时回退到直连地址。
     */
    private String resolveClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Real-IP");
        return ip == null || ip.isBlank() ? request.getRemoteAddr() : ip;
    }

    /**
     * 用于数据库审计字段的最小客户端位置快照。
     */
    private record ClientLocation(String clientIp, String city) {
        private static final ClientLocation UNKNOWN = new ClientLocation(null, IpLocation.UNKNOWN.city());
    }
}
