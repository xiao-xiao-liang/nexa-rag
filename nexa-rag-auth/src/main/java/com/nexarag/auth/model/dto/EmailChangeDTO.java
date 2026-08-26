package com.nexarag.auth.model.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/**
 * 当前邮箱与新邮箱均完成独立验证码验证的换绑请求数据传输对象。
 */
@Getter
@Setter
public class EmailChangeDTO {

    /** 当前生效旧邮箱的验证码。 */
    @Valid
    @NotNull(message = "旧邮箱验证码不能为空")
    private EmailVerificationDTO oldEmailVerification;

    /** 待绑定新邮箱的验证码。 */
    @Valid
    @NotNull(message = "新邮箱验证码不能为空")
    private EmailVerificationDTO newEmailVerification;
}
