package com.nexarag.auth.model.vo;

import java.time.LocalDateTime;

/**
 * 已绑定第三方身份展示对象，不返回稳定 subject 或任何第三方 Token。
 *
 * @param externalIdentityId 精确解绑时使用的绑定记录 ID
 * @param providerCode 第三方提供方编码
 * @param bindTime 绑定时间
 */
public record ExternalIdentityVO(Long externalIdentityId, String providerCode, LocalDateTime bindTime) {
}
