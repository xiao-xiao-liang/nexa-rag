package com.nexarag.auth.service;

import com.nexarag.auth.model.dto.CreateTenantDTO;
import com.nexarag.auth.model.vo.TenantVO;

/** 管理全局管理员创建的企业租户。 */
public interface TenantService {
    /** 创建企业租户并使当前管理员成为唯一初始所有者。 */
    TenantVO createEnterpriseTenant(CreateTenantDTO createDTO);
}
