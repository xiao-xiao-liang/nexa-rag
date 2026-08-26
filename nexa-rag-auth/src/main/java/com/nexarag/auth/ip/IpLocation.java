package com.nexarag.auth.ip;

/**
 * 客户端 IP 的市级约略地区，不保存精确坐标或完整地址。
 *
 * @param city 市级地区；无法解析时为“未知地区”
 */
public record IpLocation(String city) {

    /** 无法可靠解析 IP 地区时使用的展示值。 */
    public static final IpLocation UNKNOWN = new IpLocation("未知地区");
}
