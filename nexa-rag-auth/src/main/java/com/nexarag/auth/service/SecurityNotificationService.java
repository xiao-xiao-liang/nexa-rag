package com.nexarag.auth.service;
/** 异步投递账号安全操作通知，失败不影响已提交业务事务。 */
public interface SecurityNotificationService { void notifyUser(Long userId, String eventSummary); }
