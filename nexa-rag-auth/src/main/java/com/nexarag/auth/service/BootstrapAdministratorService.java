package com.nexarag.auth.service;

/**
 * 根据部署配置幂等创建保留管理员身份。
 */
public interface BootstrapAdministratorService {

    /**
     * 在启用且配置完整时初始化保留管理员；其他情况下不执行任何写入。
     */
    void initializeIfEnabled();
}
