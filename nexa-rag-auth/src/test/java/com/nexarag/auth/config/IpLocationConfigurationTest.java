package com.nexarag.auth.config;

import com.nexarag.auth.ip.factory.IpLocationStrategyFactory;
import com.nexarag.auth.ip.strategy.IpLocationStrategy;
import com.nexarag.auth.ip.strategy.LocalIpLocationStrategy;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * IP 地区策略装配测试。
 */
class IpLocationConfigurationTest {

    /**
     * 策略工厂和当前策略装配后应能正常创建容器。
     */
    @Test
    void shouldCreateCurrentStrategyWithoutCircularDependency() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.registerBean(IpLocationProperties.class, () -> {
                IpLocationProperties properties = new IpLocationProperties();
                properties.setProvider("local");
                return properties;
            });
            context.register(LocalIpLocationStrategy.class, IpLocationStrategyFactory.class, IpLocationConfiguration.class);

            context.refresh();

            assertThat(context.getBean(IpLocationStrategy.class)).isInstanceOf(LocalIpLocationStrategy.class);
        }
    }
}
