package com.nexarag.boot.config;

import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Jackson 全局序列化配置，解决前端 JavaScript 64 位长整型（Long）精度丢失问题。
 */
@Configuration
public class JacksonConfiguration {

    /**
     * 将 Long/long 类型在 JSON 序列化时统一转为字符串输出。
     *
     * @return Jackson 自定义配置
     */
    @Bean
    public Jackson2ObjectMapperBuilderCustomizer jackson2ObjectMapperBuilderCustomizer() {
        return builder -> {
            builder.serializerByType(Long.class, ToStringSerializer.instance);
            builder.serializerByType(Long.TYPE, ToStringSerializer.instance);
        };
    }
}
