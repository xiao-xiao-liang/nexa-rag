package com.nexarag.auth.model.vo;
import java.time.LocalDateTime;
/** 当前账号可查看的脱敏安全活动展示对象。 */
public record SecurityAuditEventVO(Long eventId,String eventType,Integer eventResult,String maskedIp,String city,String detailSummary,LocalDateTime createTime) { }
