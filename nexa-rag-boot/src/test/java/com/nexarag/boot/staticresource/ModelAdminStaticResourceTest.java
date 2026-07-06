package com.nexarag.boot.staticresource;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 模型临时管理页静态资源测试。
 */
class ModelAdminStaticResourceTest {

    @Test
    void modelAdminHtmlShouldExist() {
        ClassPathResource resource = new ClassPathResource("static/model-admin.html");

        assertThat(resource.exists()).isTrue();
    }
}
