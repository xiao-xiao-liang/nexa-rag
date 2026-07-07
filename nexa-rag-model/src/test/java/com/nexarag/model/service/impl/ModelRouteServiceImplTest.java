package com.nexarag.model.service.impl;

import com.nexarag.common.exception.ClientException;
import com.nexarag.model.config.ModelGovernanceProperties;
import com.nexarag.model.dto.ModelRouteCreateRequest;
import com.nexarag.model.entity.ModelGovernanceConfig;
import com.nexarag.model.entity.ModelRoute;
import com.nexarag.model.enums.ModelGovernanceBindingMode;
import com.nexarag.model.enums.ModelRouteStrategy;
import com.nexarag.model.enums.ModelType;
import com.nexarag.model.governance.DefaultModelGovernancePolicyFactory;
import com.nexarag.model.service.ModelGovernanceConfigService;
import com.nexarag.model.service.ModelRouteConfigService;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 模型路由服务实现测试。
 */
class ModelRouteServiceImplTest {

    @Test
    void createRouteShouldAutoCreateRouteGovernanceWhenEnabled() {
        DefaultModelGovernancePolicyFactory policyFactory = mock(DefaultModelGovernancePolicyFactory.class);
        ModelGovernanceConfigService governanceConfigService = mock(ModelGovernanceConfigService.class);
        ModelGovernanceProperties properties = new ModelGovernanceProperties();
        properties.getGovernance().setAutoCreateDefault(Boolean.TRUE);
        when(policyFactory.createForRoute(eq("chat"), eq(ModelType.CHAT))).thenReturn(ModelGovernanceConfig.builder()
                .bindingMode(ModelGovernanceBindingMode.ROUTE)
                .routeKey("chat")
                .enabled(Boolean.TRUE)
                .build());

        TestableModelRouteServiceImpl service = new TestableModelRouteServiceImpl(policyFactory,
                governanceConfigService, properties);
        ModelRoute route = service.createRoute(ModelRouteCreateRequest.builder()
                .routeKey("chat")
                .modelType(ModelType.CHAT)
                .strategy(ModelRouteStrategy.PRIMARY_BACKUP)
                .remark("默认聊天路由")
                .build());

        assertThat(route.getRouteId()).isNotNull();
        assertThat(service.savedRoute).isSameAs(route);
        assertThat(service.registryBumpCount).isEqualTo(1);
        verify(governanceConfigService).saveDefaultIfAbsent(argThat(config ->
                config.getBindingMode() == ModelGovernanceBindingMode.ROUTE
                        && "chat".equals(config.getRouteKey())));
    }

    @Test
    void deleteRouteShouldFailWhenRouteHasCandidates() {
        ModelRouteConfigService routeConfigService = mock(ModelRouteConfigService.class);
        when(routeConfigService.existsByRouteId(2001L)).thenReturn(true);
        TestableModelRouteServiceImpl service = new TestableModelRouteServiceImpl(routeConfigService);
        service.existingRoute = ModelRoute.builder()
                .routeId(2001L)
                .routeKey("chat")
                .modelType(ModelType.CHAT)
                .strategy(ModelRouteStrategy.PRIMARY_BACKUP)
                .enabled(Boolean.TRUE)
                .build();

        assertThatThrownBy(() -> service.deleteRoute(2001L))
                .isInstanceOf(ClientException.class)
                .hasMessageContaining("请先移除路由下的模型配置");
    }

    private static class TestableModelRouteServiceImpl extends ModelRouteServiceImpl {

        private ModelRoute savedRoute;
        private ModelRoute existingRoute;
        private int registryBumpCount;

        private TestableModelRouteServiceImpl(DefaultModelGovernancePolicyFactory policyFactory,
                                              ModelGovernanceConfigService governanceConfigService,
                                              ModelGovernanceProperties properties) {
            this(policyFactory, governanceConfigService, properties, mock(ModelRouteConfigService.class));
        }

        private TestableModelRouteServiceImpl(ModelRouteConfigService routeConfigService) {
            this(new DefaultModelGovernancePolicyFactory(), mock(ModelGovernanceConfigService.class),
                    new ModelGovernanceProperties(), routeConfigService);
        }

        private TestableModelRouteServiceImpl(DefaultModelGovernancePolicyFactory policyFactory,
                                              ModelGovernanceConfigService governanceConfigService,
                                              ModelGovernanceProperties properties,
                                              ModelRouteConfigService routeConfigService) {
            super(null, null, policyFactory, governanceConfigService, properties, routeConfigService);
        }

        @Override
        protected boolean existsByRouteKey(String routeKey, Long excludedRouteId) {
            return false;
        }

        @Override
        protected boolean saveRoute(ModelRoute route) {
            this.savedRoute = route;
            return true;
        }

        @Override
        protected ModelRoute getRequiredRoute(Long routeId) {
            return existingRoute;
        }

        @Override
        protected long bumpRegistryVersionAndPublish() {
            this.registryBumpCount++;
            return registryBumpCount;
        }
    }
}
