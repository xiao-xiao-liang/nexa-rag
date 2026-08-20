package com.nexarag.document.tenant;

/**
 * 租户领域的固定常量，维护单租户过渡阶段的稳定标识。
 */
public final class TenantConstants {

    /** 默认租户标识。 */
    public static final String DEFAULT_TENANT_ID = "default-tenant";

    /** 默认知识库标识。 */
    public static final long DEFAULT_KNOWLEDGE_BASE_ID = 1L;

    private TenantConstants() {
    }
}
