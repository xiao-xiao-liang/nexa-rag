package com.nexarag.auth.model.dataobject;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/** 登录设备会话的脱敏持久化摘要，对应 auth_device_session 表。 */
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
@TableName("auth_device_session")
public class DeviceSessionDO {
    @TableId(value = "device_session_id", type = IdType.INPUT) private Long deviceSessionId;
    private Long userId;
    private String saTokenSessionKeyHash;
    private String deviceIdHash;
    private String deviceName;
    private String deviceLabel;
    private String maskedIp;
    private String city;
    private LocalDateTime loginTime;
    private LocalDateTime lastActiveTime;
    private LocalDateTime revokedTime;
}
