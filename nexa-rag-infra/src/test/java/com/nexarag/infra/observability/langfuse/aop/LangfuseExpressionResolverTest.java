package com.nexarag.infra.observability.langfuse.aop;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Langfuse 返回值表达式解析测试。
 */
class LangfuseExpressionResolverTest {

    private final LangfuseExpressionResolver resolver = new LangfuseExpressionResolver();

    @Test
    void shouldResolveSupportedResultValuesAndFilterUnsafeValues() {
        Map<String, Object> result = Map.of("quality", new EvidenceQuality(List.of("chunk-1", "chunk-2"), true));

        Map<String, Object> attributes = resolver.resolveResultAttributes(new String[]{
                "nexa.evidence.accepted_count=#result['quality'].acceptedChunks().size()",
                "nexa.evidence.sufficient=#result['quality'].sufficient()",
                "invalid.key=#result['quality'].sufficient()",
                "nexa.evidence.chunk_ids=#result['quality'].acceptedChunks()"}, result);

        assertThat(attributes).containsExactlyInAnyOrderEntriesOf(Map.of(
                "nexa.evidence.accepted_count", 2,
                "nexa.evidence.sufficient", true));
    }

    private record EvidenceQuality(List<String> acceptedChunks, boolean sufficient) {
    }
}
