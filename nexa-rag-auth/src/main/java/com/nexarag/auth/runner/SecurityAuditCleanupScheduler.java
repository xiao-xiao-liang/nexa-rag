package com.nexarag.auth.runner;
import com.nexarag.auth.service.SecurityAuditService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
/** 每日清理超过 180 天的安全审计记录。 */
@Slf4j @Component @RequiredArgsConstructor public class SecurityAuditCleanupScheduler {
 private final SecurityAuditService securityAuditService;
 @Scheduled(cron="0 20 3 * * *") public void cleanup(){ int count=securityAuditService.cleanupExpiredEvents(); if(count>0)log.info("已清理过期安全审计记录，count={}",count); }
}
