package com.nexarag.model.route;

import com.nexarag.model.config.ModelProfileProperties;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 权重模型路由选择器测试。
 */
class WeightedModelRouteSelectorTest {

    @Test
    void shouldKeepOnlyPositiveWeightCandidates() {
        WeightedModelRouteSelector selector = new WeightedModelRouteSelector(new Random(1));

        List<ModelRouteDecision> selected = selector.orderCandidates(List.of(
                decision("a", 0, 1),
                decision("b", 10, 2),
                decision("c", 20, 3)
        ));

        assertThat(selected).extracting(ModelRouteDecision::profileName)
                .containsExactlyInAnyOrder("b", "c");
    }

    private ModelRouteDecision decision(String profileName, Integer weight, Integer priority) {
        ModelProfileProperties profile = ModelProfileProperties.builder()
                .provider("OPENAI")
                .baseUrl("https://api.openai.com/v1")
                .modelName("gpt-4.1-mini")
                .build();
        return new ModelRouteDecision(profileName, profile, false, priority, weight, null, null);
    }
}
