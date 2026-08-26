package com.nexarag.auth.ip.factory;

import com.nexarag.auth.ip.strategy.IpLocationStrategy;
import com.nexarag.common.exception.ServiceException;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * IP 地区策略工厂，根据部署配置从已注册策略中选择唯一实现。
 *
 * <p>新增提供商时只需注册新的策略 Bean，无需修改工厂选择分支。</p>
 */
@Component
public class IpLocationStrategyFactory {

    private static final String DEFAULT_PROVIDER = "tencent";

    private final Map<String, IpLocationStrategy> strategyMap;

    public IpLocationStrategyFactory(List<IpLocationStrategy> strategies) {
        this.strategyMap = buildStrategyMap(strategies);
    }

    /**
     * 根据策略标识创建当前部署使用的策略。
     *
     * @param provider 配置中的策略标识
     * @return 已校验部署配置的 IP 地区策略
     */
    public IpLocationStrategy create(String provider) {
        String providerName = normalizeProvider(provider);
        IpLocationStrategy strategy = strategyMap.get(providerName);
        if (strategy == null) {
            throw new ServiceException("不支持的 IP 地区策略：" + providerName);
        }

        // 1. 仅校验选中策略所需的第三方配置。
        strategy.validateConfiguration();

        // 2. 返回唯一生效策略。
        return strategy;
    }

    private Map<String, IpLocationStrategy> buildStrategyMap(List<IpLocationStrategy> strategies) {
        Map<String, IpLocationStrategy> result = new HashMap<>(strategies.size());
        for (IpLocationStrategy strategy : strategies) {
            String providerName = normalizeProvider(strategy.getProvider());
            if (result.putIfAbsent(providerName, strategy) != null) {
                throw new IllegalStateException("存在重复的 IP 地区策略：" + providerName);
            }
        }
        return Map.copyOf(result);
    }

    private String normalizeProvider(String provider) {
        if (provider == null || provider.isBlank()) {
            return DEFAULT_PROVIDER;
        }
        return provider.trim().toLowerCase(Locale.ROOT);
    }
}
