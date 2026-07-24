package com.nexarag.model.config;

import com.nexarag.model.toolkits.prompt.PromptSnapshotCache;
import com.nexarag.model.execution.ModelExecutionTemplate;
import com.nexarag.model.governance.ModelGovernanceExecutor;
import com.nexarag.model.governance.ModelGovernanceResolver;
import com.nexarag.model.registry.ModelRegistry;
import com.nexarag.model.registry.ModelRegistryRefresher;
import com.nexarag.model.route.ModelRouter;
import com.nexarag.model.route.PrimaryFallbackModelRouter;
import com.nexarag.model.route.RegistryFirstModelRouter;
import com.nexarag.model.route.WeightedModelRouteSelector;
import com.nexarag.model.toolkits.ModelSecretEncryptor;
import com.nexarag.model.service.ModelCallLogService;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 模型模块配置入口。
 */
@Configuration
@EnableConfigurationProperties({
        ModelGovernanceProperties.class,
        ModelSecretProperties.class,
        ModelRegistryRefreshProperties.class,
        PromptRefreshProperties.class
})
public class ModelConfiguration {

    /**
     * 注册 Prompt 发布和版本的进程内快照缓存。
     *
     * @return Prompt 快照缓存
     */
    @Bean
    public PromptSnapshotCache promptSnapshotCache() {
        return new PromptSnapshotCache();
    }

    /**
     * 注册模型路由器。
     *
     * @param properties 模型治理配置
     * @return 模型路由器
     */
    @Bean
    public ModelRouter modelRouter(ModelGovernanceProperties properties,
                                   ModelRegistry modelRegistry,
                                   ModelSecretEncryptor secretEncryptor) {
        return new RegistryFirstModelRouter(
                modelRegistry,
                new PrimaryFallbackModelRouter(properties),
                secretEncryptor,
                new WeightedModelRouteSelector()
        );
    }

    /**
     * 注册模型注册表启动刷新任务，确保数据库模型路由在应用启动后优先生效。
     *
     * @param modelRegistryRefresher 模型注册表刷新器
     * @return 应用启动后的刷新任务
     */
    @Bean
    public ApplicationRunner modelRegistryStartupRefresher(ModelRegistryRefresher modelRegistryRefresher) {
        return args -> modelRegistryRefresher.refreshCurrentVersion();
    }

    /**
     * 注册模型执行模板。
     *
     * @param modelRouter         模型路由器
     * @param modelCallLogService 模型调用日志服务
     * @param executor            模型治理执行器
     * @param resolver            模型治理配置解析器
     * @return 模型执行模板
     */
    @Bean
    public ModelExecutionTemplate modelExecutionTemplate(ModelRouter modelRouter,
                                                         ModelCallLogService modelCallLogService,
                                                         ModelGovernanceExecutor executor,
                                                         ModelGovernanceResolver resolver) {
        return new ModelExecutionTemplate(modelRouter, modelCallLogService, executor, resolver);
    }

    /**
     * 注册模型密钥加密器。
     *
     * @param properties 模型密钥配置
     * @return 模型密钥加密器
     */
    @Bean
    public ModelSecretEncryptor modelSecretEncryptor(ModelSecretProperties properties) {
        return new ModelSecretEncryptor(properties.getMasterKey());
    }
}
