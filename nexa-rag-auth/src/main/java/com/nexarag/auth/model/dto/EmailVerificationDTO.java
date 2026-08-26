package com.nexarag.auth.model.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * 已登录账号验证单个邮箱验证码的请求数据传输对象。
 */
@Getter
@Setter
public class EmailVerificationDTO {

    /** 待验证的邮箱地址。 */
    @NotBlank(message = "邮箱不能为空")
    @Email(message = "邮箱格式不合法")
    @Size(max = 320, message = "邮箱长度不能超过 320 个字符")
    private String email;

    /** 验证码挑战ID。 */
    @NotNull(message = "验证码挑战不能为空")
    @Positive(message = "验证码挑战不合法")
    private Long challengeId;

    /** 用户输入的六位验证码。 */
    @NotBlank(message = "验证码不能为空")
    @Pattern(regexp = "\\d{6}", message = "验证码必须为六位数字")
    private String verificationCode;
}
