package com.nexarag.model.config;

import com.nexarag.model.prompt.domain.PromptExecutionSnapshot;
import com.nexarag.model.toolkits.prompt.PromptRender;
import com.nexarag.model.prompt.domain.PromptVariableSchema;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotatedBeanDefinitionReader;
import org.springframework.context.annotation.ConfigurationClassPostProcessor;
import org.springframework.context.support.GenericApplicationContext;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 模型模块配置测试，验证 Prompt 运行时只依赖数据库版本快照。
 */
class ModelConfigurationTest {
    @Test
    void shouldNotRegisterLocalPromptRepositoryAndShouldRenderFromExecutionSnapshot() {
        PromptExecutionSnapshot snapshot = PromptExecutionSnapshot.of(Map.of("chat.rewrite.instruction",
                new PromptExecutionSnapshot.PromptSnapshot("chat.rewrite.instruction", 101L, 11L, 1L,
                        "问题：{{query}}", PromptVariableSchema.of(List.of("query"), List.of("query")))));

        // 1. 通过请求级版本快照渲染，验证生产渲染不依赖 classpath 下的 Prompt 文件。
        String content = new PromptRender().render(snapshot, "chat.rewrite.instruction", Map.of("query", "RAG")).content();

        // 2. 解析模型配置的 Bean 定义，验证旧本地模板仓储不再作为运行时 Bean 注册。
        try (GenericApplicationContext context = new GenericApplicationContext()) {
            new AnnotatedBeanDefinitionReader(context).register(ModelConfiguration.class);
            new ConfigurationClassPostProcessor().postProcessBeanDefinitionRegistry(context);
            assertThat(content).isEqualTo("问题：RAG");
            assertThat(context.containsBeanDefinition("promptTemplateRepository")).isFalse();
        }
    }
}
