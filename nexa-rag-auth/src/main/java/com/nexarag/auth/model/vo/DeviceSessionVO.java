package com.nexarag.auth.model.vo;

import java.time.LocalDateTime;

/** 设备会话安全展示对象，不包含完整 Token、IP 或设备原始标识。 */
public record DeviceSessionVO(Long deviceSessionId, String deviceId, String deviceName, String deviceLabel,
                              String maskedIp, String city, LocalDateTime loginTime, LocalDateTime lastActiveTime) { }
