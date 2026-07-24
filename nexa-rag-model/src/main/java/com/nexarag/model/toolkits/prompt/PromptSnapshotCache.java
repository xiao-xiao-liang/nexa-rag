package com.nexarag.model.toolkits.prompt;

import com.nexarag.model.entity.prompt.PromptRelease;
import com.nexarag.model.entity.prompt.PromptVersion;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Prompt 发布及版本的进程内有界缓存，不设置 TTL。
 */
public class PromptSnapshotCache {

    private static final int DEFAULT_MAXIMUM_SIZE = 1_000;

    private final Map<String, PromptRelease> currentReleaseCache;
    private final Map<String, PromptVersion> versionCache;

    /**
     * 使用默认最大缓存条目数创建缓存。
     */
    public PromptSnapshotCache() {
        this(DEFAULT_MAXIMUM_SIZE);
    }

    /**
     * 创建 Prompt 快照缓存。
     *
     * @param maximumSize 每类缓存最大条目数
     */
    public PromptSnapshotCache(int maximumSize) {
        if (maximumSize <= 0) {
            throw new IllegalArgumentException("Prompt缓存最大条目数必须大于0");
        }
        this.currentReleaseCache = createBoundedCache(maximumSize);
        this.versionCache = createBoundedCache(maximumSize);
    }

    /**
     * 获取或加载当前发布记录，缓存键仅为 Prompt 编码。
     *
     * @param promptCode Prompt 编码
     * @param loader     发布记录加载器
     * @return 当前发布记录
     */
    public synchronized PromptRelease getOrLoadCurrent(String promptCode, Supplier<PromptRelease> loader) {
        return currentReleaseCache.computeIfAbsent(promptCode, key -> loader.get());
    }

    /**
     * 获取或加载指定版本，缓存键为 Prompt 编码和版本ID。
     *
     * @param promptCode Prompt 编码
     * @param versionId  版本ID
     * @param loader     版本加载器
     * @return 不可变 Prompt 版本
     */
    public synchronized PromptVersion getOrLoadVersion(String promptCode, Long versionId, Supplier<PromptVersion> loader) {
        return versionCache.computeIfAbsent(promptCode + ':' + versionId, key -> loader.get());
    }

    /**
     * 失效指定 Prompt 的当前发布缓存，不影响不可变版本缓存。
     *
     * @param promptCode Prompt 编码
     */
    public synchronized void invalidateCurrent(String promptCode) {
        currentReleaseCache.remove(promptCode);
    }

    private <T> Map<String, T> createBoundedCache(int maximumSize) {
        return new LinkedHashMap<>(maximumSize, 0.75F, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, T> eldest) {
                return size() > maximumSize;
            }
        };
    }
}
