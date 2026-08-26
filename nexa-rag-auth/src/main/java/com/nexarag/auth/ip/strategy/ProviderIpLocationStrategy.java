package com.nexarag.auth.ip.strategy;

import org.springframework.beans.factory.annotation.Qualifier;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标记可被 IP 地区策略工厂收集的原始提供方策略。
 *
 * <p>部署配置生成的当前生效策略不应带有此标记，避免其被工厂再次收集而形成循环依赖。</p>
 */
@Target({ElementType.TYPE, ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Qualifier
public @interface ProviderIpLocationStrategy {
}
