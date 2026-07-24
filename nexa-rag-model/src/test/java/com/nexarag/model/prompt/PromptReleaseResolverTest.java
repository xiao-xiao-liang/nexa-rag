package com.nexarag.model.prompt;

import com.nexarag.model.entity.prompt.PromptDefinition;
import com.nexarag.model.entity.prompt.PromptRelease;
import com.nexarag.model.entity.prompt.PromptVersion;
import com.nexarag.model.prompt.domain.PromptCanaryRule;
import com.nexarag.model.prompt.domain.PromptExecutionSnapshot;
import com.nexarag.model.prompt.domain.PromptVariableSchema;
import com.nexarag.model.mapper.PromptDefinitionMapper;
import com.nexarag.model.mapper.PromptReleaseMapper;
import com.nexarag.model.mapper.PromptVersionMapper;
import com.nexarag.model.toolkits.prompt.PromptReleaseResolver;
import com.nexarag.model.toolkits.prompt.PromptSnapshotCache;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Prompt 发布解析器测试。
 */
class PromptReleaseResolverTest {

    @Test
    void shouldResolveStableVersionWhenNoCanaryReleaseExists() {
        PromptDefinitionMapper definitionMapper = mock(PromptDefinitionMapper.class);
        PromptReleaseMapper releaseMapper = mock(PromptReleaseMapper.class);
        PromptVersionMapper versionMapper = mock(PromptVersionMapper.class);
        when(definitionMapper.selectByPromptCode("chat.answer")).thenReturn(definition(1L, 11L, 1L));
        when(releaseMapper.selectById(11L)).thenReturn(release(11L, 1L, 101L, null, null));
        when(versionMapper.selectById(101L)).thenReturn(version(101L, "正式正文"));

        PromptExecutionSnapshot snapshot = new PromptReleaseResolver(definitionMapper, releaseMapper, versionMapper,
                new PromptSnapshotCache()).resolve(Set.of("chat.answer"), "user-1");

        assertThat(snapshot.get("chat.answer").versionId()).isEqualTo(101L);
        assertThat(snapshot.get("chat.answer").content()).isEqualTo("正式正文");
        assertThat(snapshot.get("chat.answer").releaseRevision()).isEqualTo(1L);
    }

    @Test
    void shouldKeepCanarySelectionStableForSameSubjectAndRevision() {
        PromptDefinitionMapper definitionMapper = mock(PromptDefinitionMapper.class);
        PromptReleaseMapper releaseMapper = mock(PromptReleaseMapper.class);
        PromptVersionMapper versionMapper = mock(PromptVersionMapper.class);
        when(definitionMapper.selectByPromptCode("chat.answer")).thenReturn(definition(1L, 11L, 7L));
        when(releaseMapper.selectById(11L)).thenReturn(release(11L, 7L, 101L, 102L, new PromptCanaryRule(50).toJson()));
        when(versionMapper.selectById(101L)).thenReturn(version(101L, "正式正文"));
        when(versionMapper.selectById(102L)).thenReturn(version(102L, "灰度正文"));
        PromptReleaseResolver resolver = new PromptReleaseResolver(definitionMapper, releaseMapper, versionMapper,
                new PromptSnapshotCache());

        PromptExecutionSnapshot first = resolver.resolve(Set.of("chat.answer"), "user-1");
        PromptExecutionSnapshot second = resolver.resolve(Set.of("chat.answer"), "user-1");

        assertThat(first.get("chat.answer").versionId()).isEqualTo(second.get("chat.answer").versionId());
        assertThat(first.get("chat.answer").releaseRevision()).isEqualTo(7L);
    }

    @Test
    void shouldRecalculateCanarySelectionAfterReleaseRevisionChanges() {
        PromptDefinitionMapper definitionMapper = mock(PromptDefinitionMapper.class);
        PromptReleaseMapper releaseMapper = mock(PromptReleaseMapper.class);
        PromptVersionMapper versionMapper = mock(PromptVersionMapper.class);
        PromptSnapshotCache cache = new PromptSnapshotCache();
        when(definitionMapper.selectByPromptCode("chat.answer"))
                .thenReturn(definition(1L, 11L, 7L))
                .thenReturn(definition(1L, 12L, 8L));
        when(releaseMapper.selectById(11L)).thenReturn(release(11L, 7L, 101L, 102L, new PromptCanaryRule(0).toJson()));
        when(releaseMapper.selectById(12L)).thenReturn(release(12L, 8L, 101L, 102L, new PromptCanaryRule(100).toJson()));
        when(versionMapper.selectById(101L)).thenReturn(version(101L, "正式正文"));
        when(versionMapper.selectById(102L)).thenReturn(version(102L, "灰度正文"));
        PromptReleaseResolver resolver = new PromptReleaseResolver(definitionMapper, releaseMapper, versionMapper, cache);

        PromptExecutionSnapshot beforeRelease = resolver.resolve(Set.of("chat.answer"), "user-1");
        cache.invalidateCurrent("chat.answer");
        PromptExecutionSnapshot afterRelease = resolver.resolve(Set.of("chat.answer"), "user-1");

        assertThat(beforeRelease.get("chat.answer").versionId()).isEqualTo(101L);
        assertThat(afterRelease.get("chat.answer").versionId()).isEqualTo(102L);
        assertThat(afterRelease.get("chat.answer").releaseRevision()).isEqualTo(8L);
    }

    @Test
    void shouldRejectCurrentReleaseThatBelongsToAnotherPrompt() {
        PromptDefinitionMapper definitionMapper = mock(PromptDefinitionMapper.class);
        PromptReleaseMapper releaseMapper = mock(PromptReleaseMapper.class);
        PromptVersionMapper versionMapper = mock(PromptVersionMapper.class);
        when(definitionMapper.selectByPromptCode("chat.answer")).thenReturn(definition(1L, 11L, 1L));
        when(releaseMapper.selectById(11L)).thenReturn(PromptRelease.builder().releaseId(11L).promptId(2L)
                .releaseRevision(1L).stableVersionId(101L).build());
        when(versionMapper.selectById(101L)).thenReturn(version(101L, "正式正文"));

        assertThatThrownBy(() -> new PromptReleaseResolver(definitionMapper, releaseMapper, versionMapper,
                new PromptSnapshotCache()).resolve(Set.of("chat.answer"), "user-1"))
                .isInstanceOf(com.nexarag.common.exception.ClientException.class)
                .hasMessageContaining("归属不正确");
    }

    private PromptDefinition definition(Long promptId, Long releaseId, Long revision) {
        return PromptDefinition.builder().promptId(promptId).promptCode("chat.answer").enabled(true)
                .currentReleaseId(releaseId).currentReleaseRevision(revision).build();
    }

    private PromptRelease release(Long releaseId, Long revision, Long stableVersionId, Long canaryVersionId, String canaryRule) {
        return PromptRelease.builder().releaseId(releaseId).promptId(1L).releaseRevision(revision)
                .stableVersionId(stableVersionId).canaryVersionId(canaryVersionId).canaryRule(canaryRule).build();
    }

    private PromptVersion version(Long versionId, String content) {
        return PromptVersion.builder().versionId(versionId).promptId(1L).content(content)
                .variableSchemaSnapshot(PromptVariableSchema.of(java.util.List.of("query"), java.util.List.of("query")).toJson()).build();
    }
}
