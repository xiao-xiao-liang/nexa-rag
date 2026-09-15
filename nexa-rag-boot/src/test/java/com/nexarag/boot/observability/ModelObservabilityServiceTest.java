package com.nexarag.boot.observability;

import com.nexarag.common.error.BaseErrorCode;
import com.nexarag.common.exception.ServiceException;
import com.nexarag.infra.observability.langfuse.LangfuseQueryClient;
import com.nexarag.infra.observability.langfuse.api.LangfuseObservationDTO;
import com.nexarag.infra.observability.langfuse.api.LangfuseObservationPageDTO;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 模型观测聚合服务测试。
 */
class ModelObservabilityServiceTest {

    @Test
    void shouldOnlyUseCompleteProviderUsageForPercentiles() {
        LangfuseQueryClient client = mock(LangfuseQueryClient.class);
        when(client.fetchGenerations(any(Instant.class), any(Instant.class), nullable(String.class)))
                .thenReturn(new LangfuseObservationPageDTO(List.of(
                        observation("complete", Map.of("input", 100, "output", 20, "total", 120), 80,
                                Map.of("nexa.rag_token_status", "COMPLETE", "nexa.rag_template_overhead_tokens", 7)),
                        observation("partial", Map.of("input", 200, "output", 40, "total", 240), 40,
                                Map.of("nexa.rag_token_status", "PARTIAL")),
                        observation("unknown", Map.of(), 10, Map.of("nexa.rag_token_status", "COMPLETE"))),
                        new LangfuseObservationPageDTO.Meta(null)));
        ModelObservabilityService service = new ModelObservabilityService(client);

        ModelObservabilityOverviewVO overview = service.overview(new ModelObservabilityQuery(null, null, null, null,
                null, 50));

        assertThat(overview.available()).isTrue();
        assertThat(overview.generationCount()).isEqualTo(3);
        assertThat(overview.validUsageCount()).isEqualTo(2);
        assertThat(overview.tokenDataCompleteness()).isEqualTo(2D / 3D);
        assertThat(overview.inputTokensP50()).isEqualTo(100);
        assertThat(overview.inputTokensP95()).isEqualTo(200);
        assertThat(overview.ttftMsP50()).isEqualTo(40);
        assertThat(overview.templateOverheadTokensP50()).isEqualTo(7);
    }

    @Test
    void shouldReturnAvailableFalseWhenLangfuseIsUnavailable() {
        LangfuseQueryClient client = mock(LangfuseQueryClient.class);
        when(client.fetchGenerations(any(Instant.class), any(Instant.class), nullable(String.class)))
                .thenThrow(new ServiceException("不可用", BaseErrorCode.REMOTE_ERROR));
        ModelObservabilityService service = new ModelObservabilityService(client);

        ModelObservabilityOverviewVO overview = service.overview(new ModelObservabilityQuery(null, null, null, null,
                null, null));

        assertThat(overview.available()).isFalse();
        assertThat(overview.reason()).isEqualTo("LANGFUSE_UNAVAILABLE");
        assertThat(overview.generationCount()).isZero();
    }

    @Test
    void shouldOnlyIncludeCompleteBreakdownsInSegmentDistribution() {
        LangfuseQueryClient client = mock(LangfuseQueryClient.class);
        when(client.fetchGenerations(any(Instant.class), any(Instant.class), nullable(String.class)))
                .thenReturn(new LangfuseObservationPageDTO(List.of(
                        observation("complete", Map.of("input", 100, "output", 20), 80,
                                Map.of("nexa.rag_token_status", "COMPLETE", "nexa.rag_system_tokens", 10,
                                        "nexa.rag_summary_tokens", 5, "nexa.rag_history_tokens", 20,
                                        "nexa.rag_question_tokens", 8, "nexa.rag_retrieval_tokens", 50,
                                        "nexa.rag_tool_tokens", 2, "nexa.rag_template_overhead_tokens", 5)),
                        observation("partial", Map.of("input", 200, "output", 40), 40,
                                Map.of("nexa.rag_token_status", "PARTIAL", "nexa.rag_system_tokens", 99))),
                        new LangfuseObservationPageDTO.Meta(null)));
        ModelObservabilityService service = new ModelObservabilityService(client);

        ModelTokenDistributionVO distribution = service.tokenDistribution(new ModelObservabilityQuery(null, null,
                null, null, null, null));

        assertThat(distribution.available()).isTrue();
        assertThat(distribution.completeBreakdownCount()).isEqualTo(1);
        assertThat(distribution.systemTokensP50()).isEqualTo(10);
        assertThat(distribution.retrievalTokensP50()).isEqualTo(50);
        assertThat(distribution.templateOverheadTokensP50()).isEqualTo(5);
    }

    @Test
    void shouldReadFollowingPagesBeforeApplyingLocalFilters() {
        LangfuseQueryClient client = mock(LangfuseQueryClient.class);
        when(client.fetchGenerations(any(Instant.class), any(Instant.class), nullable(String.class)))
                .thenReturn(new LangfuseObservationPageDTO(List.of(
                                observation("other-route", Map.of("input", 10, "output", 2), 10,
                                        Map.of("nexa.route_key", "other"))),
                        new LangfuseObservationPageDTO.Meta("next-page")))
                .thenReturn(new LangfuseObservationPageDTO(List.of(
                                observation("target-route", Map.of("input", 120, "output", 30), 30,
                                        Map.of("nexa.route_key", "local-qwen"))),
                        new LangfuseObservationPageDTO.Meta(null)));
        ModelObservabilityService service = new ModelObservabilityService(client);

        ModelObservabilityOverviewVO overview = service.overview(new ModelObservabilityQuery(null, null, null,
                "local-qwen", null, null));

        assertThat(overview.generationCount()).isEqualTo(1);
        assertThat(overview.inputTokensP50()).isEqualTo(120);
    }

    @Test
    void shouldReuseSameDefaultTimeBucketFromCache() {
        LangfuseQueryClient client = mock(LangfuseQueryClient.class);
        when(client.fetchGenerations(any(Instant.class), any(Instant.class), nullable(String.class)))
                .thenReturn(new LangfuseObservationPageDTO(List.of(
                                observation("generation-1", Map.of("input", 120, "output", 30), 30, Map.of())),
                        new LangfuseObservationPageDTO.Meta(null)));
        ModelObservabilityService service = new ModelObservabilityService(client);

        service.overview(null);
        service.overview(null);

        verify(client, times(1)).fetchGenerations(any(Instant.class), any(Instant.class), nullable(String.class));
    }

    @Test
    void shouldExposeTruncationWhenPageLimitIsReached() {
        LangfuseQueryClient client = mock(LangfuseQueryClient.class);
        when(client.fetchGenerations(any(Instant.class), any(Instant.class), nullable(String.class)))
                .thenReturn(new LangfuseObservationPageDTO(List.of(
                                observation("generation-1", Map.of("input", 120, "output", 30), 30, Map.of())),
                        new LangfuseObservationPageDTO.Meta("next-page")));
        ModelObservabilityService service = new ModelObservabilityService(client);

        ModelObservabilityOverviewVO overview = service.overview(new ModelObservabilityQuery(
                Instant.parse("2026-09-11T00:00:00Z"), Instant.parse("2026-09-11T01:00:00Z"),
                null, null, null, null));

        assertThat(overview.truncated()).isTrue();
        verify(client, times(5)).fetchGenerations(any(Instant.class), any(Instant.class), nullable(String.class));
    }

    @Test
    void shouldReturnSafeTraceRowsWithoutUserOrContentFields() {
        LangfuseQueryClient client = mock(LangfuseQueryClient.class);
        when(client.fetchGenerations(any(Instant.class), any(Instant.class), nullable(String.class)))
                .thenReturn(new LangfuseObservationPageDTO(List.of(
                        observation("generation-1", Map.of("input", 120, "output", 30, "total", 150), 30,
                                Map.of("nexa.route_key", "local-qwen", "nexa.rag_token_status", "COMPLETE"))),
                        new LangfuseObservationPageDTO.Meta(null)));
        ModelObservabilityService service = new ModelObservabilityService(client);

        List<ModelObservabilityTraceVO> traces = service.traces(new ModelObservabilityQuery(null, null, null,
                null, null, null));

        assertThat(traces).containsExactly(new ModelObservabilityTraceVO("trace-generation-1", "generation-1",
                "2026-09-11T00:00:00Z", "Qwen3.5-4B", "local-qwen", "DEFAULT", 120, 30, 150, 30,
                "COMPLETE"));
    }

    private LangfuseObservationDTO observation(String id, Map<String, Object> usage, Number ttft,
                                                Map<String, Object> metadata) {
        return new LangfuseObservationDTO(id, "trace-" + id, "rag.answer", "GENERATION",
                "2026-09-11T00:00:00Z", "2026-09-11T00:00:01Z", "Qwen3.5-4B", usage, 100, ttft,
                metadata, "DEFAULT", null, "conversation-1", "user-1");
    }
}
