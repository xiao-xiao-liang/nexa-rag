package com.nexarag.auth.model.dataobject;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.LocalDateTime;

/** 安全审计事件持久化对象，对应 auth_security_audit_event 表。 */
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @TableName("auth_security_audit_event")
public class SecurityAuditEventDO {
    @TableId(value="event_id", type=IdType.INPUT) private Long eventId;
    private Long userId; private Long actorUserId; private String eventType; private Integer eventResult;
    private Long deviceSessionId; private String maskedIp; private String city; private String detailSummary; private LocalDateTime createTime;
}
