package com.nexarag.auth.service;

/**
 * 默认管理员受控恢复入口，仅供部署运维命令调用，禁止映射为公开 Web 接口。
 */
public interface ControlledAdministratorRecoveryService {

    /**
     * 依据已审计工单恢复保留管理员，并更换其待验证的预置邮箱。
     *
     * @param workOrderId 已审计的运维工单标识
     * @param presetEmail 新的预置邮箱
     */
    void recover(String workOrderId, String presetEmail);
}
