package com.nexarag.workflow.service;

import com.nexarag.model.config.ModelProfileProperties;
import com.nexarag.model.route.ModelRouteContext;
import com.nexarag.model.route.ModelRouter;
import com.nexarag.retrieval.model.RetrievalChunk;
import com.nexarag.retrieval.retriever.ParentContextExpansionRetriever;
import com.nexarag.workflow.config.ModelInputEvidenceProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 按最终回答模型的输入窗口选择证据。
 *
 * <p>完整父片段优先；当它不能完整放入窗口时，只回退至 Rerank 已命中的直接子片段，
 * 绝不补入相邻兄弟片段或截断正文。</p>
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class ModelInputEvidenceSelector {

    private final ModelRouter modelRouter;
    private final ModelInputEvidenceProperties properties;

    /**
     * 按模型输入边界筛选证据。
     *
     * @param acceptedChunks     证据质量节点接纳的候选
     * @param directHitChunks    父子替换前的直接 Rerank 命中
     * @param staticPromptTokens 不含证据的提示词、问题和历史估算 Token 数
     * @param routeKey           模型路由键
     * @return 不会超过模型输入预算的完整证据片段
     */
    public List<RetrievalChunk> select(List<RetrievalChunk> acceptedChunks, List<RetrievalChunk> directHitChunks,
                                       int staticPromptTokens, String routeKey) {
        if (acceptedChunks == null || acceptedChunks.isEmpty()) {
            return List.of();
        }
        int inputBudget = availableInputTokens(routeKey);
        int remainingTokens = inputBudget - Math.max(0, staticPromptTokens);
        if (remainingTokens <= 0) {
            log.warn("回答静态提示词已达到模型输入边界，routeKey={}，静态估算Token={}，证据预算={}",
                    routeKey, staticPromptTokens, inputBudget);
            return List.of();
        }

        List<RetrievalChunk> selected = new ArrayList<>();
        Set<String> selectedChunkIds = new HashSet<>();
        for (RetrievalChunk candidate : acceptedChunks) {
            if (!hasBody(candidate) || !selectedChunkIds.add(candidate.chunkId())) {
                continue;
            }
            int candidateTokens = estimateTokens(candidate.content());
            if (candidateTokens <= remainingTokens) {
                selected.add(candidate);
                remainingTokens -= candidateTokens;
                continue;
            }
            selectedChunkIds.remove(candidate.chunkId());
            if (!ParentContextExpansionRetriever.PARENT_CONTEXT_CHANNEL.equals(candidate.channel())) {
                continue;
            }
            for (RetrievalChunk childHit : directChildren(candidate, directHitChunks)) {
                if (!selectedChunkIds.add(childHit.chunkId())) {
                    continue;
                }
                int childTokens = estimateTokens(childHit.content());
                if (childTokens <= remainingTokens) {
                    selected.add(childHit);
                    remainingTokens -= childTokens;
                } else {
                    selectedChunkIds.remove(childHit.chunkId());
                }
            }
        }
        return List.copyOf(selected);
    }

    /**
     * 计算当前路由所有可切换模型共同可承受的输入 Token 上限。
     *
     * <p>备模型同样参与最小值计算，避免主模型故障切换后因更小窗口导致请求失败。</p>
     *
     * @param routeKey 模型路由键
     * @return 可供提示词和证据共同使用的 Token 预算
     */
    public int availableInputTokens(String routeKey) {
        return modelRouter.plan(new ModelRouteContext(routeKey, false)).candidates().stream()
                .mapToInt(decision -> availableInputTokens(decision.profile()))
                .min()
                .orElse(fallbackAvailableInputTokens());
    }

    private int availableInputTokens(ModelProfileProperties profile) {
        int contextWindowTokens = profile.getContextWindowTokens() > 0
                ? profile.getContextWindowTokens() : properties.getFallbackContextWindowTokens();
        int reservedOutputTokens = profile.getReservedOutputTokens() > 0
                ? profile.getReservedOutputTokens() : properties.getFallbackReservedOutputTokens();
        return availableInputTokens(contextWindowTokens, reservedOutputTokens);
    }

    private int fallbackAvailableInputTokens() {
        return availableInputTokens(properties.getFallbackContextWindowTokens(),
                properties.getFallbackReservedOutputTokens());
    }

    private int availableInputTokens(int contextWindowTokens, int reservedOutputTokens) {
        return Math.max(0, contextWindowTokens - reservedOutputTokens - properties.getInputSafetyMarginTokens());
    }

    private List<RetrievalChunk> directChildren(RetrievalChunk parent, List<RetrievalChunk> directHitChunks) {
        if (directHitChunks == null || directHitChunks.isEmpty()) {
            return List.of();
        }
        return directHitChunks.stream()
                .filter(this::hasBody)
                .filter(chunk -> parent.chunkId().equals(chunk.parentChunkId()))
                .toList();
    }

    private boolean hasBody(RetrievalChunk chunk) {
        return chunk != null && StringUtils.hasText(chunk.content());
    }

    /**
     * 估算文本占用的 Token 数。
     *
     * <p>采用“一个字符最多按一个 Token”的保守策略，优先为中文内容留出安全余量。</p>
     *
     * @param content 待估算文本
     * @return 保守估算的 Token 数
     */
    public int estimateTokens(String content) {
        return content == null ? 0 : content.codePointCount(0, content.length());
    }
}
