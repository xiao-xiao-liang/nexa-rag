package com.nexarag.model.prompt;

import com.nexarag.model.entity.prompt.PromptDefinition;
import com.nexarag.model.entity.prompt.PromptRelease;
import com.nexarag.model.entity.prompt.PromptVersion;
import com.nexarag.model.prompt.domain.PromptCanaryRule;
import com.nexarag.model.prompt.domain.PromptReleaseResult;
import com.nexarag.model.prompt.domain.PromptVariableSchema;
import com.nexarag.model.mapper.PromptDefinitionMapper;
import com.nexarag.model.mapper.PromptReleaseMapper;
import com.nexarag.model.mapper.PromptVersionMapper;
import com.nexarag.model.prompt.refresh.PromptRefreshPublisher;
import com.nexarag.model.prompt.refresh.PromptReleaseChangedMessage;
import com.nexarag.model.service.PromptPublishService;
import com.nexarag.model.service.impl.DefaultPromptPublishService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.transaction.support.TransactionSynchronizationManager.clearSynchronization;
import static org.springframework.transaction.support.TransactionSynchronizationManager.getSynchronizations;
import static org.springframework.transaction.support.TransactionSynchronizationManager.initSynchronization;

/**
 * Prompt 发布服务测试。
 */
class PromptPublishServiceTest {

    /**
     * 验证发布事务提交后才执行缓存失效和跨实例刷新通知。
     */
    @Test
    void shouldPublishRefreshMessageAfterTransactionCommit() {
        PromptDefinitionMapper definitionMapper = mock(PromptDefinitionMapper.class);
        PromptVersionMapper versionMapper = mock(PromptVersionMapper.class);
        PromptReleaseMapper releaseMapper = mock(PromptReleaseMapper.class);
        PromptRefreshPublisher refreshPublisher = mock(PromptRefreshPublisher.class);
        PromptDefinition definition = PromptDefinition.builder().promptId(1L).promptCode("chat.answer.current-question")
                .variableSchema(PromptVariableSchema.of(List.of("question"), List.of("question")).toJson())
                .enabled(Boolean.TRUE).currentReleaseRevision(1L).build();
        when(definitionMapper.selectByPromptCodeForUpdate(definition.getPromptCode())).thenReturn(definition);
        when(versionMapper.selectNextVersionNo(1L)).thenReturn(1L);
        PromptPublishService service = new DefaultPromptPublishService(new DefaultPromptTemplateValidator(),
                definitionMapper, versionMapper, releaseMapper, refreshPublisher);
        initSynchronization();
        try {
            // 1. 执行发布写入，但尚未触发事务提交回调。
            service.submit(definition.getPromptCode(), "请回答：{{question}}", "测试人员");
            verify(refreshPublisher, never()).publish(any(PromptReleaseChangedMessage.class));

            // 2. 模拟事务提交，并验证提交后才发布刷新消息。
            getSynchronizations().forEach(synchronization -> synchronization.afterCommit());
            verify(refreshPublisher).publish(any(PromptReleaseChangedMessage.class));
        } finally {
            clearSynchronization();
        }
    }

    /**
     * 验证提交 Prompt 时使用定义行锁串行化版本号和发布代次分配。
     */
    @Test
    void shouldLockDefinitionBeforeSubmittingPrompt() {
        PromptDefinitionMapper definitionMapper = mock(PromptDefinitionMapper.class);
        PromptVersionMapper versionMapper = mock(PromptVersionMapper.class);
        PromptReleaseMapper releaseMapper = mock(PromptReleaseMapper.class);
        PromptDefinition definition = enabledDefinition(1L, 1L, 1L);
        when(definitionMapper.selectByPromptCodeForUpdate(definition.getPromptCode())).thenReturn(definition);
        when(versionMapper.selectNextVersionNo(definition.getPromptId())).thenReturn(2L);
        PromptPublishService service = new DefaultPromptPublishService(new DefaultPromptTemplateValidator(),
                definitionMapper, versionMapper, releaseMapper);

        // 1. 提交新版本正文。
        service.submit(definition.getPromptCode(), "请回答：{{question}}", "测试人员");

        // 2. 验证服务使用定义行锁读取，而非普通读取。
        verify(definitionMapper).selectByPromptCodeForUpdate(definition.getPromptCode());
        verify(definitionMapper, never()).selectByPromptCode(definition.getPromptCode());
    }

    /**
     * 验证重复正文不会创建无意义的版本和发布记录。
     */
    @Test
    void shouldRejectDuplicateContentWhenSubmittingPrompt() {
        PromptDefinitionMapper definitionMapper = mock(PromptDefinitionMapper.class);
        PromptVersionMapper versionMapper = mock(PromptVersionMapper.class);
        PromptReleaseMapper releaseMapper = mock(PromptReleaseMapper.class);
        PromptDefinition definition = enabledDefinition(1L, 1L, 1L);
        when(definitionMapper.selectByPromptCodeForUpdate(definition.getPromptCode())).thenReturn(definition);
        when(versionMapper.selectByContentChecksum(any(), any())).thenReturn(PromptVersion.builder().versionId(2L).build());
        PromptPublishService service = new DefaultPromptPublishService(new DefaultPromptTemplateValidator(),
                definitionMapper, versionMapper, releaseMapper);

        // 1. 提交与历史版本正文相同的模板
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.submit(definition.getPromptCode(),
                        "请回答：{{question}}", "测试人员"))
                // 2. 验证拒绝重复正文且不写入版本、发布记录
                .hasMessageContaining("重复");
        verify(versionMapper, never()).insert(any(PromptVersion.class));
        verify(releaseMapper, never()).insert(any(PromptRelease.class));
    }

    /**
     * 验证灰度版本必须属于当前 Prompt。
     */
    @Test
    void shouldRejectReleaseWhenCanaryVersionDoesNotBelongToPrompt() {
        PromptDefinitionMapper definitionMapper = mock(PromptDefinitionMapper.class);
        PromptVersionMapper versionMapper = mock(PromptVersionMapper.class);
        PromptReleaseMapper releaseMapper = mock(PromptReleaseMapper.class);
        PromptDefinition definition = enabledDefinition(1L, 1L, 1L);
        when(definitionMapper.selectByPromptCodeForUpdate(definition.getPromptCode())).thenReturn(definition);
        when(versionMapper.selectById(2L)).thenReturn(PromptVersion.builder().versionId(2L).promptId(1L).build());
        when(versionMapper.selectById(3L)).thenReturn(PromptVersion.builder().versionId(3L).promptId(9L).build());
        PromptPublishService service = new DefaultPromptPublishService(new DefaultPromptTemplateValidator(),
                definitionMapper, versionMapper, releaseMapper);

        // 1. 发布一个属于其他 Prompt 的灰度版本
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.release(definition.getPromptCode(), 2L, 3L,
                        new PromptCanaryRule(10), "测试人员"))
                // 2. 验证发布被拒绝且不新增发布记录
                .hasMessageContaining("不属于");
        verify(releaseMapper, never()).insert(any(PromptRelease.class));
    }

    /**
     * 验证回滚仅追加发布记录，不改写历史版本正文。
     */
    @Test
    void shouldAppendReleaseWithoutChangingHistoricalContentWhenRollingBack() {
        PromptDefinitionMapper definitionMapper = mock(PromptDefinitionMapper.class);
        PromptVersionMapper versionMapper = mock(PromptVersionMapper.class);
        PromptReleaseMapper releaseMapper = mock(PromptReleaseMapper.class);
        PromptDefinition definition = enabledDefinition(1L, 8L, 7L);
        PromptVersion historicalVersion = PromptVersion.builder().versionId(2L).promptId(1L)
                .content("历史正文{{question}}").build();
        when(definitionMapper.selectByPromptCodeForUpdate(definition.getPromptCode())).thenReturn(definition);
        when(versionMapper.selectById(2L)).thenReturn(historicalVersion);
        PromptPublishService service = new DefaultPromptPublishService(new DefaultPromptTemplateValidator(),
                definitionMapper, versionMapper, releaseMapper);

        // 1. 回滚到历史正文对应的版本
        PromptReleaseResult result = service.rollback(definition.getPromptCode(), 2L, "测试人员");

        // 2. 验证只追加发布记录，不更新历史版本正文
        ArgumentCaptor<PromptRelease> releaseCaptor = ArgumentCaptor.forClass(PromptRelease.class);
        verify(releaseMapper).insert(releaseCaptor.capture());
        verify(versionMapper, never()).updateById(any(PromptVersion.class));
        assertThat(historicalVersion.getContent()).isEqualTo("历史正文{{question}}");
        assertThat(releaseCaptor.getValue().getStableVersionId()).isEqualTo(2L);
        assertThat(releaseCaptor.getValue().getRollbackFromReleaseId()).isEqualTo(7L);
        assertThat(result.releaseRevision()).isEqualTo(9L);
    }

    /**
     * 验证提交会新增不可变版本和正式发布记录。
     */
    @Test
    void shouldCreateImmutableVersionAndReleaseWhenSubmittingPrompt() {
        PromptDefinitionMapper definitionMapper = mock(PromptDefinitionMapper.class);
        PromptDefinition definition = PromptDefinition.builder()
                .promptId(1L)
                .promptCode("chat.answer.current-question")
                .variableSchema(PromptVariableSchema.of(List.of("question"), List.of("question")).toJson())
                .enabled(Boolean.TRUE)
                .currentReleaseRevision(1L)
                .build();
        PromptVersionMapper versionMapper = mock(PromptVersionMapper.class);
        PromptReleaseMapper releaseMapper = mock(PromptReleaseMapper.class);
        when(definitionMapper.selectByPromptCodeForUpdate("chat.answer.current-question")).thenReturn(definition);
        when(versionMapper.selectNextVersionNo(1L)).thenReturn(1L);
        PromptPublishService service = new DefaultPromptPublishService(new DefaultPromptTemplateValidator(),
                definitionMapper, versionMapper, releaseMapper);

        // 1. 提交引用已登记变量的新正文
        PromptReleaseResult result = service.submit("chat.answer.current-question", "请回答：{{question}}", "测试人员");

        // 2. 验证新版本和新发布记录均被追加，并同步更新当前发布指针
        assertThat(result.versionId()).isNotNull();
        assertThat(result.releaseId()).isNotNull();
        assertThat(result.releaseRevision()).isEqualTo(2L);
        ArgumentCaptor<PromptVersion> versionCaptor = ArgumentCaptor.forClass(PromptVersion.class);
        ArgumentCaptor<PromptRelease> releaseCaptor = ArgumentCaptor.forClass(PromptRelease.class);
        verify(versionMapper).insert(versionCaptor.capture());
        verify(releaseMapper).insert(releaseCaptor.capture());
        verify(definitionMapper).updateById(definition);
        PromptVersion version = versionCaptor.getValue();
        PromptRelease release = releaseCaptor.getValue();
        assertThat(version).satisfies(savedVersion -> {
            assertThat(savedVersion.getVersionNo()).isEqualTo(1L);
            assertThat(savedVersion.getContent()).isEqualTo("请回答：{{question}}");
        });
        assertThat(release).satisfies(savedRelease -> {
            assertThat(savedRelease.getStableVersionId()).isEqualTo(version.getVersionId());
            assertThat(savedRelease.getReleaseRevision()).isEqualTo(2L);
        });
        assertThat(definition.getCurrentReleaseId()).isEqualTo(release.getReleaseId());
        assertThat(definition.getCurrentReleaseRevision()).isEqualTo(2L);
    }

    /**
     * 创建启用的 Prompt 定义测试数据。
     */
    private PromptDefinition enabledDefinition(Long promptId, Long revision, Long releaseId) {
        return PromptDefinition.builder()
                .promptId(promptId)
                .promptCode("chat.answer.current-question")
                .variableSchema(PromptVariableSchema.of(List.of("question"), List.of("question")).toJson())
                .enabled(Boolean.TRUE)
                .currentReleaseRevision(revision)
                .currentReleaseId(releaseId)
                .build();
    }
}
