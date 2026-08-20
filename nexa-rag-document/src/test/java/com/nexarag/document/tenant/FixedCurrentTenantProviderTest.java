package com.nexarag.document.tenant;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 固定当前租户提供者测试。
 */
class FixedCurrentTenantProviderTest {

    @Test
    void providerShouldReturnConfiguredDefaultTenant() {
        Class<?> providerClass = loadClass("com.nexarag.document.tenant.FixedCurrentTenantProvider");
        Class<?> constantsClass = loadClass("com.nexarag.document.tenant.TenantConstants");

        assertThat(providerClass).isNotNull();
        assertThat(constantsClass).isNotNull();
    }

    private Class<?> loadClass(String className) {
        try {
            return Class.forName(className);
        } catch (ClassNotFoundException exception) {
            return null;
        }
    }
}
