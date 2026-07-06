package com.nexarag.model.config;

import com.nexarag.model.prompt.LocalPromptTemplateRepository;
import com.nexarag.model.prompt.PromptTemplateRepository;
import com.nexarag.model.prompt.PromptTemplateService;
import com.nexarag.model.execution.ModelExecutionTemplate;
import com.nexarag.model.governance.ModelGovernanceExecutor;
import com.nexarag.model.governance.ModelGovernanceResolver;
import com.nexarag.model.route.ModelRouter;
import com.nexarag.model.route.PrimaryFallbackModelRouter;
import com.nexarag.model.security.ModelSecretEncryptor;
import com.nexarag.model.service.ModelCallLogService;
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
        ModelRegistryRefreshProperties.class
})
public class ModelConfiguration {

    /**
     * 注册本地 Prompt 模板仓储。
     *
     * @return Prompt 模板仓储
     */
    @Bean
    public PromptTemplateRepository promptTemplateRepository() {
        return new LocalPromptTemplateRepository("classpath*:/prompts/**/*.md");
    }

    /**
     * 注册 Prompt 模板服务。
     *
     * @param repository Prompt 模板仓储
     * @return Prompt 模板服务
     */
    @Bean
    public PromptTemplateService promptTemplateService(PromptTemplateRepository repository) {
        return new PromptTemplateService(repository);
    }

    /**
     * 注册模型路由器。
     *
     * @param properties 模型治理配置
     * @return 模型路由器
     */
    @Bean
    public ModelRouter modelRouter(ModelGovernanceProperties properties) {
        return new PrimaryFallbackModelRouter(properties);
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
