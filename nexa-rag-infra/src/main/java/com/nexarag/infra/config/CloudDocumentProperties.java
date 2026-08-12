package com.nexarag.infra.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 云文档来源读取配置，统一管理飞书和语雀的访问参数。
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "nexa.cloud-document")
public class CloudDocumentProperties {

    /** 飞书来源读取配置。 */
    private FeishuProperties feishu = new FeishuProperties();

    /** 语雀来源读取配置。 */
    private YuqueProperties yuque = new YuqueProperties();

    /**
     * 飞书外部来源读取配置，包含 OpenAPI 降级和 CLI 主读取路径的参数。
     */
    @Getter
    @Setter
    public static class FeishuProperties {

        /** 飞书应用 App ID，仅 OpenAPI 降级读取使用。 */
        private String appId;

        /** 飞书应用 App Secret，仅 OpenAPI 降级读取使用。 */
        private String appSecret;

        /** 飞书开放平台 API 基础地址。 */
        private String baseUrl = "https://open.feishu.cn";

        /** 飞书 DOCX 导出任务配置。 */
        private ExportProperties export = new ExportProperties();

        /**
         * 飞书 DOCX 导出任务配置。
         */
        @Getter
        @Setter
        public static class ExportProperties {

            /** 单次导出任务最长等待时间。 */
            private Duration timeout = Duration.ofSeconds(120);

            /** 查询导出任务状态的轮询间隔。 */
            private Duration pollInterval = Duration.ofSeconds(2);

            /** 最大轮询次数，避免远端任务无界等待。 */
            private int maxPollCount = 60;
        }
    }

    /**
     * 语雀来源读取配置。
     */
    @Getter
    @Setter
    public static class YuqueProperties {

        /** 语雀个人访问令牌。 */
        private String token;
    }
}
