package com.nexarag.model.toolkits.prompt;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexarag.common.error.BaseErrorCode;
import com.nexarag.common.exception.ClientException;
import com.nexarag.model.entity.prompt.PromptDefinition;
import com.nexarag.model.entity.prompt.PromptRelease;
import com.nexarag.model.entity.prompt.PromptVersion;
import com.nexarag.model.prompt.domain.PromptCanaryRule;
import com.nexarag.model.prompt.domain.PromptExecutionSnapshot;
import com.nexarag.model.prompt.domain.PromptVariableSchema;
import com.nexarag.model.mapper.PromptDefinitionMapper;
import com.nexarag.model.mapper.PromptReleaseMapper;
import com.nexarag.model.mapper.PromptVersionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Prompt 发布解析器，按执行主体选择正式或灰度版本并生成请求级快照。
 */
@Service
@RequiredArgsConstructor
public class PromptReleaseResolver {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final PromptDefinitionMapper definitionMapper;
    private final PromptReleaseMapper releaseMapper;
    private final PromptVersionMapper versionMapper;
    private final PromptSnapshotCache snapshotCache;

    /**
     * 解析指定 Prompt 在本次执行中应绑定的不可变版本。
     *
     * @param promptCodes Prompt 编码集合
     * @param subjectId   灰度主体ID
     * @return 请求级 Prompt 版本快照
     */
    public PromptExecutionSnapshot resolve(Set<String> promptCodes, String subjectId) {
        if (promptCodes == null || promptCodes.isEmpty()) {
            throw new ClientException("Prompt编码集合不能为空");
        }
        if (!StringUtils.hasText(subjectId)) {
            throw new ClientException("Prompt灰度主体不能为空");
        }
        Map<String, PromptExecutionSnapshot.PromptSnapshot> snapshots = new LinkedHashMap<>();
        for (String promptCode : promptCodes) {
            // 1. 读取当前发布指针，并从当前发布缓存获取发布记录
            PromptDefinition definition = getEnabledDefinition(promptCode);
            PromptRelease release = snapshotCache.getOrLoadCurrent(promptCode,
                    () -> getRelease(definition.getCurrentReleaseId(), definition.getPromptId(), promptCode));

            // 2. 按发布代次和主体计算稳定灰度桶，选择正式或灰度版本
            Long versionId = selectVersionId(promptCode, subjectId, release);
            PromptVersion version = snapshotCache.getOrLoadVersion(promptCode, versionId,
                    () -> getVersion(versionId, definition.getPromptId(), promptCode));

            // 3. 将正文和变量契约复制到请求级快照，隔离后续发布变化
            snapshots.put(promptCode, new PromptExecutionSnapshot.PromptSnapshot(promptCode, version.getVersionId(),
                    release.getReleaseId(), release.getReleaseRevision(), version.getContent(),
                    PromptVariableSchema.fromJson(version.getVariableSchemaSnapshot())));
        }
        return PromptExecutionSnapshot.of(snapshots);
    }

    private Long selectVersionId(String promptCode, String subjectId, PromptRelease release) {
        if (release.getCanaryVersionId() == null || !StringUtils.hasText(release.getCanaryRule())) {
            return release.getStableVersionId();
        }
        PromptCanaryRule rule = parseCanaryRule(release.getCanaryRule());
        rule.validate();
        int bucket = calculateCanaryBucket(promptCode, release.getReleaseRevision(), subjectId);
        return bucket < rule.percentage() * 100 ? release.getCanaryVersionId() : release.getStableVersionId();
    }

    /**
     * 计算稳定灰度桶，输入包含 Prompt 编码、发布代次和主体ID。
     *
     * @param promptCode      Prompt 编码
     * @param releaseRevision 发布代次
     * @param subjectId       灰度主体ID
     * @return 0 至 9999 的灰度桶
     */
    public static int calculateCanaryBucket(String promptCode, Long releaseRevision, String subjectId) {
        return Math.floorMod((promptCode + releaseRevision + subjectId).hashCode(), 10_000);
    }

    private PromptDefinition getEnabledDefinition(String promptCode) {
        if (!StringUtils.hasText(promptCode)) {
            throw new ClientException("Prompt编码不能为空");
        }
        PromptDefinition definition = definitionMapper.selectByPromptCode(promptCode);
        if (definition == null || !Boolean.TRUE.equals(definition.getEnabled()) || definition.getCurrentReleaseId() == null) {
            throw new ClientException("Prompt不存在、未启用或未发布，promptCode=" + promptCode);
        }
        return definition;
    }

    private PromptRelease getRelease(Long releaseId, Long promptId, String promptCode) {
        PromptRelease release = releaseMapper.selectById(releaseId);
        if (release == null || !promptId.equals(release.getPromptId()) || release.getStableVersionId() == null
                || release.getReleaseRevision() == null) {
            throw new ClientException("Prompt当前发布记录不存在、归属不正确或不完整，promptCode=" + promptCode);
        }
        return release;
    }

    private PromptVersion getVersion(Long versionId, Long promptId, String promptCode) {
        PromptVersion version = versionMapper.selectById(versionId);
        if (version == null || !promptId.equals(version.getPromptId())) {
            throw new ClientException("Prompt发布版本不存在或归属不正确，promptCode=" + promptCode);
        }
        return version;
    }

    private PromptCanaryRule parseCanaryRule(String canaryRule) {
        try {
            return OBJECT_MAPPER.readValue(canaryRule, PromptCanaryRule.class);
        } catch (Exception exception) {
            throw new ClientException("Prompt灰度规则不合法", exception, BaseErrorCode.PARAM_ERROR);
        }
    }
}
