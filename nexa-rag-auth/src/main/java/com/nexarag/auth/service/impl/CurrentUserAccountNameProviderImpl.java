package com.nexarag.auth.service.impl;

import com.nexarag.auth.context.UserContext;
import com.nexarag.auth.mapper.AuthUserMapper;
import com.nexarag.auth.model.dataobject.AuthUserDO;
import com.nexarag.auth.service.CurrentUserAccountNameProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 当前请求账号名提供者实现，避免业务审计使用过期的会话展示信息。
 */
@Service
@RequiredArgsConstructor
public class CurrentUserAccountNameProviderImpl implements CurrentUserAccountNameProvider {

    private final AuthUserMapper authUserMapper;

    /**
     * {@inheritDoc}
     */
    @Override
    public String getCurrentAccountName() {
        // 1. 按当前认证用户读取权威账号名，避免账号改名后继续写入旧值。
        AuthUserDO user = authUserMapper.selectById(Long.valueOf(UserContext.getUserId()));
        if (user == null || !StringUtils.hasText(user.getAccountName())) {
            throw new IllegalStateException("当前登录用户不存在或缺少账号名");
        }
        return user.getAccountName();
    }
}
