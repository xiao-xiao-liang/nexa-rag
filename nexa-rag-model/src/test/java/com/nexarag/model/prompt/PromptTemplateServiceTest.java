package com.nexarag.model.prompt;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Prompt 模板服务测试。
 */
class PromptTemplateServiceTest {

    @Test
    void shouldRenderMarkdownPromptWithMustacheVariables() {
        LocalPromptTemplateRepository repository = new LocalPromptTemplateRepository("classpath:/prompts/**/*.md");
        PromptTemplateService service = new PromptTemplateService(repository);

        String prompt = service.render("chat.query-rewrite", Map.of(
                "query", "什么是RAG",
                "history", List.of(Map.of("role", "USER", "content", "你好"))
        ));

        assertThat(prompt).contains("用户问题：什么是RAG");
        assertThat(prompt).contains("- USER：你好");
    }
}
