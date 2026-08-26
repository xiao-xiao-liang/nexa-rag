package com.nexarag.auth.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * 账号密码登录请求数据传输对象。
 */
@Getter
@Setter
public class AccountPasswordLoginDTO {

    /** 用户输入的账号名。 */
    @NotBlank(message = "账号名不能为空")
    @Size(max = 39, message = "账号名长度不能超过 39 个字符")
    private String accountName;

    /** 用户输入的明文密码，仅用于本次验证，禁止记录或返回。 */
    @NotBlank(message = "密码不能为空")
    @Size(max = 1024, message = "密码长度不能超过 1024 个字符")
    private String password;
}
