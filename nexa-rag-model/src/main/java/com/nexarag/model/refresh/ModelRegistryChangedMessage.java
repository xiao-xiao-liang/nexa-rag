package com.nexarag.model.refresh;

import com.nexarag.model.enums.ModelRefreshChannel;

/**
 * 模型注册表变更消息。
 *
 * @param versionNo 全局模型注册表版本号
 * @param channel   刷新通道
 */
public record ModelRegistryChangedMessage(long versionNo, ModelRefreshChannel channel) {
}
