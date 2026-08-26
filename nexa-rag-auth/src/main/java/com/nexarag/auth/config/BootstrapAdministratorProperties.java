package com.nexarag.auth.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 部署时初始化保留管理员账号的环境配置。
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "nexa.auth.bootstrap-admin")
public class BootstrapAdministratorProperties {

    /** 是否启用管理员初始化；默认关闭。 */
    private boolean enabled;

    /** 保留管理员账号名。 */
    private String accountName;

    /** 管理员首次激活时必须验证的预置邮箱。 */
    private String email;
}
