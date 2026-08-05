package com.nexarag.model.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.nexarag.common.exception.ClientException;
import com.nexarag.model.dto.prompt.PromptResponse;
import com.nexarag.model.dto.prompt.PromptUpdateDTO;
import com.nexarag.model.entity.prompt.PromptDefinition;
import com.nexarag.model.entity.prompt.PromptRelease;
import com.nexarag.model.entity.prompt.PromptVersion;
import com.nexarag.model.prompt.PromptTemplateValidator;
import com.nexarag.model.prompt.domain.PromptVariableSchema;
import com.nexarag.model.mapper.PromptDefinitionMapper;
import com.nexarag.model.mapper.PromptReleaseMapper;
import com.nexarag.model.mapper.PromptVersionMapper;
import com.nexarag.model.service.PromptManagementService;
import com.samskivert.mustache.Mustache;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;

/**
 * 默认 Prompt 在线管理服务，负责聚合定义、版本与发布历史，并生成脱敏预览。
 */
@Service
@RequiredArgsConstructor
public class PromptManagementServiceImpl implements PromptManagementService {

    private final PromptDefinitionMapper definitionMapper;
    private final PromptVersionMapper versionMapper;
    private final PromptReleaseMapper releaseMapper;
    private final PromptTemplateValidator templateValidator;

    /**
     * 查询全部 Prompt 定义摘要。
     *
     * @return Prompt 定义列表
     */
    @Override
    public List<PromptResponse> listPrompts() {
        // 1. 查询全部 Prompt 稳定定义
        return definitionMapper.selectList(new LambdaQueryWrapper<PromptDefinition>()
                        .orderByAsc(PromptDefinition::getPromptCode))
                .stream()
                // 2. 仅组装定义摘要，避免列表查询加载完整历史正文
                .map(this::toSummaryResponse)
                .toList();
    }

    /**
     * 查询指定 Prompt 的完整管理详情。
     *
     * @param promptCode Prompt 编码
     * @return Prompt 定义、版本与发布历史
     */
    @Override
    public PromptResponse getPrompt(String promptCode) {
        // 1. 查询指定 Prompt 定义并尽早校验不存在场景
        PromptDefinition definition = getDefinition(promptCode);

        // 2. 分别查询不可变版本和追加式发布记录
        List<PromptResponse.Version> versions = versionMapper.selectList(new LambdaQueryWrapper<PromptVersion>()
                        .eq(PromptVersion::getPromptId, definition.getPromptId())
                        .orderByDesc(PromptVersion::getVersionNo))
                .stream().map(this::toVersionResponse).toList();
        List<PromptResponse.Release> releases = releaseMapper.selectList(new LambdaQueryWrapper<PromptRelease>()
                        .eq(PromptRelease::getPromptId, definition.getPromptId())
                        .orderByDesc(PromptRelease::getReleaseRevision))
                .stream().map(this::toReleaseResponse).toList();

        // 3. 组装包含完整历史的管理详情
        return toResponse(definition, versions, releases);
    }

    /**
     * 生成模板脱敏预览。
     *
     * @param promptCode Prompt 编码
     * @param content    待预览模板正文
     * @param variables  预留请求变量，不使用真实值
     * @return 使用脱敏示例变量渲染后的正文
     */
    @Override
    public String preview(String promptCode, String content, Map<String, Object> variables) {
        // 1. 查询定义并校验模板变量契约
        PromptDefinition definition = getDefinition(promptCode);
        PromptVariableSchema schema = PromptVariableSchema.fromJson(definition.getVariableSchema());
        templateValidator.validate(promptCode, content, schema);

        // 2. 仅构造固定脱敏示例变量，禁止使用请求中可能含敏感信息的真实值
        Map<String, Object> maskedVariables = schema.allowed().stream()
                .collect(java.util.stream.Collectors.toMap(variable -> variable,
                        variable -> "示例" + variable));

        // 3. 本地渲染正文，不写数据库且不调用模型
        return Mustache.compiler().escapeHTML(false).compile(content).execute(maskedVariables);
    }

    /**
     * 更新 Prompt 基础定义（名称、变量契约与启用状态）。
     *
     * @param promptCode Prompt 编码
     * @param request    更新请求
     * @return 更新后的 Prompt 详情
     */
    @Override
    public PromptResponse updatePrompt(String promptCode, PromptUpdateDTO request) {
        // 1. 查询 Prompt 稳定定义
        PromptDefinition definition = getDefinition(promptCode);

        // 2. 按需更新名称
        if (StringUtils.hasText(request.name())) {
            definition.setName(request.name().trim());
        }

        // 3. 按需更新变量契约（校验合法 JSON）
        if (StringUtils.hasText(request.variableSchema())) {
            PromptVariableSchema.fromJson(request.variableSchema().trim());
            definition.setVariableSchema(request.variableSchema().trim());
        }

        // 4. 按需更新启用状态
        if (request.enabled() != null) {
            definition.setEnabled(request.enabled());
        }

        // 5. 更新定义落库
        definition.setUpdateTime(java.time.LocalDateTime.now());
        definitionMapper.updateById(definition);

        // 6. 返回最新管理详情
        return getPrompt(promptCode);
    }

    private PromptDefinition getDefinition(String promptCode) {
        if (!StringUtils.hasText(promptCode)) {
            throw new ClientException("Prompt编码不能为空");
        }
        PromptDefinition definition = definitionMapper.selectByPromptCode(promptCode);
        if (definition == null) {
            throw new ClientException("Prompt不存在，promptCode=" + promptCode);
        }
        return definition;
    }

    private PromptResponse toSummaryResponse(PromptDefinition definition) {
        return toResponse(definition, List.of(), List.of());
    }

    private PromptResponse toResponse(PromptDefinition definition, List<PromptResponse.Version> versions,
                                      List<PromptResponse.Release> releases) {
        return PromptResponse.builder()
                .promptCode(definition.getPromptCode())
                .name(definition.getName())
                .variableSchema(definition.getVariableSchema())
                .enabled(definition.getEnabled())
                .currentReleaseId(definition.getCurrentReleaseId())
                .currentReleaseRevision(definition.getCurrentReleaseRevision())
                .versions(versions)
                .releases(releases)
                .build();
    }

    private PromptResponse.Version toVersionResponse(PromptVersion version) {
        return PromptResponse.Version.builder()
                .versionId(version.getVersionId()).versionNo(version.getVersionNo()).content(version.getContent())
                .createdBy(version.getCreatedBy()).createdAt(version.getCreatedAt()).remark(version.getRemark()).build();
    }

    private PromptResponse.Release toReleaseResponse(PromptRelease release) {
        return PromptResponse.Release.builder()
                .releaseId(release.getReleaseId()).stableVersionId(release.getStableVersionId())
                .canaryVersionId(release.getCanaryVersionId()).canaryRule(release.getCanaryRule())
                .releaseRevision(release.getReleaseRevision()).releasedBy(release.getReleasedBy())
                .releasedAt(release.getReleasedAt()).rollbackFromReleaseId(release.getRollbackFromReleaseId())
                .remark(release.getRemark()).build();
    }
}
