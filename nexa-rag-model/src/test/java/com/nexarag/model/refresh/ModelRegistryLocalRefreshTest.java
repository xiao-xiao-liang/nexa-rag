package com.nexarag.model.refresh;

import com.nexarag.model.config.ModelRegistryRefreshProperties;
import com.nexarag.model.enums.ModelRefreshChannel;
import com.nexarag.model.registry.ModelRegistryRefresher;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * 本地模型注册表刷新测试。
 */
class ModelRegistryLocalRefreshTest {

    @Test
    void localChannelShouldRefreshCurrentJvmDirectly() {
        ModelRegistryRefresher refresher = mock(ModelRegistryRefresher.class);
        ModelRegistryRefreshProperties properties = new ModelRegistryRefreshProperties();
        properties.setRefreshChannel(ModelRefreshChannel.LOCAL);
        DefaultModelRegistryChangePublisher publisher =
                new DefaultModelRegistryChangePublisher(properties, null, refresher);

        publisher.publish(9L);

        verify(refresher).refreshIfNewer(9L);
    }
}
