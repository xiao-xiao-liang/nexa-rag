package com.nexarag.auth.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * IP 地区策略的部署配置；第三方 Key 仅允许环境变量注入。
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "nexa.auth.ip-location")
public class IpLocationProperties {

    /** 策略名称：local、tencent 或 amap。 */
    private String provider = "tencent";

    /** 腾讯地图 WebService Key。 */
    private String tencentKey;

    /** 腾讯地图 WebService 签名密钥，仅用于服务端计算 sig。 */
    private String tencentSk;

    /** 高德 WebService Key。 */
    private String amapKey;
}
