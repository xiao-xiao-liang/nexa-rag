package com.nexarag.auth.config;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 认证身份 BloomFilter Bean 注册测试。 */
class BloomFilterConfigurationTest {

    @Test
    void shouldDeclareThreeHardCodedIdentityBloomFilterBeans() {
        assertThat(Arrays.stream(BloomFilterConfiguration.class.getDeclaredMethods())
                .map(method -> method.getName()))
                .contains("emailIdentityBloomFilter", "externalUserProviderBloomFilter", "externalSubjectBloomFilter");
    }

    @Test
    void shouldNotRetainIdentityBloomPropertiesClass() {
        assertThatThrownBy(() -> Class.forName("com.nexarag.auth.config.AuthIdentityBloomProperties"))
                .isInstanceOf(ClassNotFoundException.class);
    }
}
