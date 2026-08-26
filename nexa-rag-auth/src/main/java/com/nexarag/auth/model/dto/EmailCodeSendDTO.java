package com.nexarag.auth.model.dto;

import com.nexarag.auth.enums.EmailVerificationPurpose;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * 邮箱验证码发送请求数据传输对象。
 */
@Getter
@Setter
public class EmailCodeSendDTO {

    /** 待验证邮箱地址。 */
    @NotBlank(message = "邮箱不能为空")
    @Email(message = "邮箱格式不合法")
    @Size(max = 320, message = "邮箱长度不能超过 320 个字符")
    private String email;

    /** 本次验证码允许使用的业务用途。 */
    @NotNull(message = "验证码用途不能为空")
    private EmailVerificationPurpose purpose;
}
