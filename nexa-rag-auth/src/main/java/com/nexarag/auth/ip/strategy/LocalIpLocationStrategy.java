package com.nexarag.auth.ip.strategy;

import com.nexarag.auth.ip.IpLocation;
import org.springframework.stereotype.Component;

/**
 * 本地策略，不向外部服务发送客户端 IP，仅返回未知地区。
 */
@Component
@ProviderIpLocationStrategy
public class LocalIpLocationStrategy implements IpLocationStrategy {

    @Override
    public String getProvider() {
        return "local";
    }

    @Override
    public IpLocation locate(String ip) {
        return IpLocation.UNKNOWN;
    }
}
