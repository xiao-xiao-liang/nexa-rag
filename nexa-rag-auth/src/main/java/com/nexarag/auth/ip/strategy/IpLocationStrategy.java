package com.nexarag.auth.ip.strategy;

import com.nexarag.auth.ip.IpLocation;

/**
 * 将可信客户端 IP 解析为市级约略地区的策略接口。
 */
public interface IpLocationStrategy {

    /**
     * 返回与 IP 地区配置 provider 对应的策略标识。
     *
     * @return 策略标识
     */
    String getProvider();

    /**
     * 校验当前策略运行所需的部署配置。
     *
     * <p>仅工厂选中该策略时调用，避免未启用的第三方策略因缺少密钥导致应用无法启动。</p>
     */
    default void validateConfiguration() {
        // 本地策略无需额外配置。
    }

    /**
     * 解析客户端 IP；失败时必须返回未知地区，不能阻断认证流程。
     *
     * @param ip 可信客户端 IP
     * @return 市级约略地区
     */
    IpLocation locate(String ip);
}
