package com.nexarag.auth.model.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * 邮箱密码登录请求数据传输对象。
 */
@Getter
@Setter
public class EmailPasswordLoginDTO {

    /** 用户输入邮箱。 */
    @NotBlank(message = "邮箱不能为空")
    @Email(message = "邮箱格式不合法")
    @Size(max = 320, message = "邮箱长度不能超过 320 个字符")
    private String email;

    /** 用户输入明文密码，仅用于本次校验。 */
    @NotBlank(message = "密码不能为空")
    @Size(max = 1024, message = "密码长度不能超过 1024 个字符")
    private String password;
}
