package com.nexarag.auth.service;
/** 记录不含敏感凭据的账号安全审计事件。 */
public interface SecurityAuditService { void recordSuccess(Long userId, String eventType, String detailSummary); java.util.List<com.nexarag.auth.model.vo.SecurityAuditEventVO> listCurrentUserEvents(); int cleanupExpiredEvents(); }
