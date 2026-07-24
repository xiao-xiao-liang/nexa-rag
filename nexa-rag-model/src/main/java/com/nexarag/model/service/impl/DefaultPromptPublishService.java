package com.nexarag.model.service.impl;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.nexarag.common.exception.ClientException;
import com.nexarag.model.entity.prompt.PromptDefinition;
import com.nexarag.model.entity.prompt.PromptRelease;
import com.nexarag.model.entity.prompt.PromptVersion;
import com.nexarag.model.prompt.domain.PromptCanaryRule;
import com.nexarag.model.prompt.domain.PromptReleaseResult;
import com.nexarag.model.prompt.PromptTemplateValidator;
import com.nexarag.model.prompt.domain.PromptVariableSchema;
import com.nexarag.model.mapper.PromptDefinitionMapper;
import com.nexarag.model.mapper.PromptReleaseMapper;
import com.nexarag.model.mapper.PromptVersionMapper;
import com.nexarag.model.prompt.refresh.PromptRefreshPublisher;
import com.nexarag.model.prompt.refresh.PromptReleaseChangedMessage;
import com.nexarag.model.service.PromptPublishService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;

/**
 * 默认 Prompt 发布服务，确保版本正文不可变且发布记录只追加。
 */
@Service
public class DefaultPromptPublishService implements PromptPublishService {

    private final PromptTemplateValidator templateValidator;
    private final PromptDefinitionMapper definitionMapper;
    private final PromptVersionMapper versionMapper;
    private final PromptReleaseMapper releaseMapper;
    private final PromptRefreshPublisher refreshPublisher;

    /**
     * 创建带发布后刷新能力的 Prompt 发布服务。
     *
     * @param templateValidator  模板校验器
     * @param definitionMapper   Prompt 定义数据访问接口
     * @param versionMapper      Prompt 版本数据访问接口
     * @param releaseMapper      Prompt 发布记录数据访问接口
     * @param refreshPublisher   发布后刷新消息发布器
     */
    @Autowired
    public DefaultPromptPublishService(PromptTemplateValidator templateValidator,
                                       PromptDefinitionMapper definitionMapper,
                                       PromptVersionMapper versionMapper,
                                       PromptReleaseMapper releaseMapper,
                                       PromptRefreshPublisher refreshPublisher) {
        this.templateValidator = templateValidator;
        this.definitionMapper = definitionMapper;
        this.versionMapper = versionMapper;
        this.releaseMapper = releaseMapper;
        this.refreshPublisher = refreshPublisher;
    }

    /**
     * 创建不启用事务后刷新回调的发布服务，供既有单元测试使用。
     *
     * @param templateValidator 模板校验器
     * @param definitionMapper  Prompt 定义数据访问接口
     * @param versionMapper     Prompt 版本数据访问接口
     * @param releaseMapper     Prompt 发布记录数据访问接口
     */
    public DefaultPromptPublishService(PromptTemplateValidator templateValidator,
                                       PromptDefinitionMapper definitionMapper,
                                       PromptVersionMapper versionMapper,
                                       PromptReleaseMapper releaseMapper) {
        this(templateValidator, definitionMapper, versionMapper, releaseMapper, null);
    }

    /**
     * 校验新模板、追加版本和发布记录，并更新当前发布指针。
     *
     * @param promptCode Prompt 编码
     * @param content    模板正文
     * @param operator   操作人
     * @return 发布结果
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public PromptReleaseResult submit(String promptCode, String content, String operator) {
        // 1. 查询启用的 Prompt 定义并校验模板变量契约
        PromptDefinition definition = getEnabledDefinition(promptCode);
        PromptVariableSchema schema = PromptVariableSchema.fromJson(definition.getVariableSchema());
        templateValidator.validate(promptCode, content, schema);

        // 2. 校验正文未重复后追加不可变版本
        String checksum = sha256(content);
        if (versionMapper.selectByContentChecksum(definition.getPromptId(), checksum) != null) {
            throw new ClientException("Prompt模板正文与历史版本重复，promptCode=" + promptCode);
        }
        PromptVersion version = PromptVersion.builder()
                .versionId(IdWorker.getId())
                .promptId(definition.getPromptId())
                .versionNo(versionMapper.selectNextVersionNo(definition.getPromptId()))
                .content(content)
                .contentChecksum(checksum)
                .variableSchemaSnapshot(definition.getVariableSchema())
                .createdBy(requireOperator(operator))
                .createdAt(LocalDateTime.now())
                .build();
        versionMapper.insert(version);

        // 3. 将新增版本作为正式版本追加发布记录并更新当前指针
        return appendRelease(definition, version.getVersionId(), null, null, operator, null);
    }

    /**
     * 为既有版本追加正式或灰度发布记录。
     *
     * @param promptCode      Prompt 编码
     * @param stableVersionId 正式版本ID
     * @param canaryVersionId 灰度版本ID
     * @param canaryRule      灰度规则
     * @param operator        操作人
     * @return 发布结果
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public PromptReleaseResult release(String promptCode, Long stableVersionId, Long canaryVersionId,
                                       PromptCanaryRule canaryRule, String operator) {
        // 1. 查询定义并验证正式、灰度版本归属
        PromptDefinition definition = getEnabledDefinition(promptCode);
        validateVersionBelongsToPrompt(definition, stableVersionId);
        if (canaryVersionId != null) {
            validateVersionBelongsToPrompt(definition, canaryVersionId);
            if (canaryRule == null) {
                throw new ClientException("Prompt灰度发布必须提供灰度规则，promptCode=" + promptCode);
            }
            canaryRule.validate();
        } else if (canaryRule != null) {
            throw new ClientException("未指定灰度版本时不能提供灰度规则，promptCode=" + promptCode);
        }

        // 2. 追加发布记录并更新定义当前发布指针
        return appendRelease(definition, stableVersionId, canaryVersionId, canaryRule, operator, null);
    }

    /**
     * 回滚到指定历史版本，仅追加发布记录，不修改历史正文。
     *
     * @param promptCode      Prompt 编码
     * @param targetVersionId 目标版本ID
     * @param operator        操作人
     * @return 发布结果
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public PromptReleaseResult rollback(String promptCode, Long targetVersionId, String operator) {
        // 1. 确认目标历史版本属于当前 Prompt
        PromptDefinition definition = getEnabledDefinition(promptCode);
        validateVersionBelongsToPrompt(definition, targetVersionId);

        // 2. 追加指向目标版本的发布记录，保留原发布记录和正文
        return appendRelease(definition, targetVersionId, null, null, operator, definition.getCurrentReleaseId());
    }

    private PromptReleaseResult appendRelease(PromptDefinition definition, Long stableVersionId, Long canaryVersionId,
                                              PromptCanaryRule canaryRule, String operator, Long rollbackFromReleaseId) {
        long revision = definition.getCurrentReleaseRevision() == null ? 1L : definition.getCurrentReleaseRevision() + 1L;
        PromptRelease release = PromptRelease.builder()
                .releaseId(IdWorker.getId())
                .promptId(definition.getPromptId())
                .stableVersionId(stableVersionId)
                .canaryVersionId(canaryVersionId)
                .canaryRule(canaryRule == null ? null : canaryRule.toJson())
                .releaseRevision(revision)
                .releasedBy(requireOperator(operator))
                .releasedAt(LocalDateTime.now())
                .rollbackFromReleaseId(rollbackFromReleaseId)
                .build();
        releaseMapper.insert(release);
        definition.setCurrentReleaseId(release.getReleaseId());
        definition.setCurrentReleaseRevision(revision);
        definitionMapper.updateById(definition);
        registerRefreshAfterCommit(definition.getPromptCode(), release.getReleaseId(), revision);
        return new PromptReleaseResult(stableVersionId, release.getReleaseId(), revision);
    }

    /**
     * 注册事务提交后的本机缓存失效和跨实例刷新通知。
     *
     * @param promptCode      Prompt 编码
     * @param releaseId       发布记录 ID
     * @param releaseRevision 发布代次
     */
    private void registerRefreshAfterCommit(String promptCode, Long releaseId, long releaseRevision) {
        if (refreshPublisher == null || !TransactionSynchronizationManager.isSynchronizationActive()) {
            return;
        }
        PromptReleaseChangedMessage message = new PromptReleaseChangedMessage(promptCode, releaseId, releaseRevision);
        // 1. 仅在数据库事务成功提交后执行缓存失效和 Redis 通知。
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                // 2. 发布器保证本机精确失效先于跨实例消息发送。
                refreshPublisher.publish(message);
            }
        });
    }

    /**
     * 查询并锁定启用的 Prompt 定义，串行化同一 Prompt 的版本号和发布代次分配。
     *
     * @param promptCode Prompt 编码
     * @return 已锁定且启用的 Prompt 定义
     */
    private PromptDefinition getEnabledDefinition(String promptCode) {
        if (!StringUtils.hasText(promptCode)) {
            throw new ClientException("Prompt编码不能为空");
        }
        // 1. 在当前事务内锁定定义行，避免并发发布读取到相同版本号或发布代次。
        PromptDefinition definition = definitionMapper.selectByPromptCodeForUpdate(promptCode);
        if (definition == null || !Boolean.TRUE.equals(definition.getEnabled())) {
            throw new ClientException("Prompt不存在或未启用，promptCode=" + promptCode);
        }
        return definition;
    }

    private void validateVersionBelongsToPrompt(PromptDefinition definition, Long versionId) {
        if (versionId == null) {
            throw new ClientException("Prompt版本ID不能为空，promptCode=" + definition.getPromptCode());
        }
        PromptVersion version = versionMapper.selectById(versionId);
        if (version == null || !definition.getPromptId().equals(version.getPromptId())) {
            throw new ClientException("Prompt版本不属于当前定义，promptCode=" + definition.getPromptCode());
        }
    }

    private String requireOperator(String operator) {
        if (!StringUtils.hasText(operator)) {
            throw new ClientException("Prompt操作人不能为空");
        }
        return operator;
    }

    private String sha256(String content) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(content.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("当前运行环境不支持SHA-256摘要算法", exception);
        }
    }
}
