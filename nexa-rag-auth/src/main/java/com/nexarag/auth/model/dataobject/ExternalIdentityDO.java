package com.nexarag.auth.model.dataobject;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/** 第三方稳定身份绑定数据对象，对应 auth_external_identity 表。 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@TableName("auth_external_identity")
public class ExternalIdentityDO {
    /** 第三方身份绑定ID。 */
    @TableId(value = "external_identity_id", type = IdType.INPUT)
    private Long externalIdentityId;
    /** 本地用户ID。 */
    private Long userId;
    /** 提供方编码。 */
    private String providerCode;
    /** 提供方不可变主体标识。 */
    private String providerSubject;
    /** 绑定时间。 */
    private LocalDateTime bindTime;
    /** 创建时间。 */
    private LocalDateTime createTime;
    /** 更新时间。 */
    private LocalDateTime updateTime;
}
