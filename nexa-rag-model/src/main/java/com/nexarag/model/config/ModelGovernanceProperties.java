package com.nexarag.model.config;

import com.nexarag.model.enums.ModelGovernanceBindingMode;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.HashMap;
import java.util.Map;

/**
 * 模型治理配置。
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "nexa.model")
public class ModelGovernanceProperties {

    /**
     * 模型治理全局配置。
     */
    private Governance governance = new Governance();

    /**
     * 模型 Profile 集合。
     */
    private Map<String, ModelProfileProperties> profiles = new HashMap<>();

    /**
     * 业务路由配置集合。
     */
    private Map<String, ModelRouteProperties> routes = new HashMap<>();

    /**
     * 模型治理绑定配置，决定运行时按模型配置还是按业务路由读取治理参数。
     */
    @Getter
    @Setter
    public static class Governance {

        /**
         * 治理配置绑定模式：CONFIG按模型配置生效，ROUTE按业务路由生效。
         */
        private ModelGovernanceBindingMode bindingMode = ModelGovernanceBindingMode.CONFIG;

        /**
         * 新增模型配置时是否自动创建默认治理配置。
         */
        private Boolean autoCreateDefault = Boolean.TRUE;
    }
}
