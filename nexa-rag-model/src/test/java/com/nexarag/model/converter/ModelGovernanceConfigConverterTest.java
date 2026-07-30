package com.nexarag.model.converter;

import com.nexarag.model.dto.ModelGovernanceConfigRequest;
import com.nexarag.model.dto.ModelGovernanceConfigResponse;
import com.nexarag.model.entity.ModelGovernanceConfig;
import com.nexarag.model.enums.ModelGovernanceBindingMode;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 模型治理配置转换器测试，验证响应转换和局部更新边界。
 */
class ModelGovernanceConfigConverterTest {

    private final ModelGovernanceConfigConverter converter =
            Mappers.getMapper(ModelGovernanceConfigConverter.class);

    /**
     * 验证请求中的空字段不覆盖已保存字段，且绑定身份始终由服务端维护。
     */
    @Test
    void patchShouldOnlyApplyNonNullPolicyFieldsAndKeepBindingIdentity() {
        ModelGovernanceConfig target = ModelGovernanceConfig.builder()
                .governanceId(1L)
                .bindingMode(ModelGovernanceBindingMode.CONFIG)
                .configId(2L)
                .enabled(false)
                .maxAttempts(1)
                .build();
        ModelGovernanceConfigRequest request = ModelGovernanceConfigRequest.builder()
                .bindingMode(ModelGovernanceBindingMode.ROUTE)
                .routeKey("chat")
                .enabled(true)
                .build();

        // 1. 应用前端提交的局部治理参数
        converter.patch(request, target);

        // 2. 验证仅非空策略字段发生变化
        assertThat(target.getEnabled()).isTrue();
        assertThat(target.getMaxAttempts()).isEqualTo(1);

        // 3. 验证绑定身份不能由请求体修改
        assertThat(target.getGovernanceId()).isEqualTo(1L);
        assertThat(target.getBindingMode()).isEqualTo(ModelGovernanceBindingMode.CONFIG);
        assertThat(target.getConfigId()).isEqualTo(2L);
        assertThat(target.getRouteKey()).isNull();
    }

    /**
     * 验证实体字段能够转换为前端响应字段。
     */
    @Test
    void toResponseShouldMapGovernanceFields() {
        ModelGovernanceConfig source = ModelGovernanceConfig.builder()
                .governanceId(1L)
                .bindingMode(ModelGovernanceBindingMode.ROUTE)
                .routeKey("chat")
                .enabled(true)
                .maxAttempts(2)
                .build();

        // 1. 转换实体为响应对象
        ModelGovernanceConfigResponse response = converter.toResponse(source);

        // 2. 验证关键字段完整映射
        assertThat(response.governanceId()).isEqualTo(1L);
        assertThat(response.bindingMode()).isEqualTo(ModelGovernanceBindingMode.ROUTE);
        assertThat(response.routeKey()).isEqualTo("chat");
        assertThat(response.enabled()).isTrue();
        assertThat(response.maxAttempts()).isEqualTo(2);
    }
}
