package com.nexarag.model.config;

import com.nexarag.model.prompt.LocalPromptTemplateRepository;
import com.nexarag.model.prompt.PromptTemplateRepository;
import com.nexarag.model.prompt.PromptTemplateService;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 模型模块配置入口。
 */
@Configuration
@EnableConfigurationProperties(ModelGovernanceProperties.class)
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
}
