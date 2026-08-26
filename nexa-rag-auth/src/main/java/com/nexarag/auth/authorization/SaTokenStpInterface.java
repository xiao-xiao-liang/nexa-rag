package com.nexarag.auth.authorization;

import cn.dev33.satoken.stp.StpInterface;
import cn.dev33.satoken.stp.StpUtil;
import cn.dev33.satoken.session.SaSession;
import com.nexarag.auth.mapper.AuthUserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Sa-Token 权限接口实现，仅依据稳定用户ID读取数据库中的全局角色权限。
 */
@Component
@RequiredArgsConstructor
public class SaTokenStpInterface implements StpInterface {

    /** Sa-Token 账号 Session 中的权限缓存键。 */
    private static final String PERMISSION_CACHE_KEY = "auth:permissions";

    /** Sa-Token 账号 Session 中的角色缓存键。 */
    private static final String ROLE_CACHE_KEY = "auth:roles";

    private final AuthUserMapper authUserMapper;

    /**
     * 获取指定登录主体的权限编码。
     *
     * @param loginId Sa-Token 登录主体
     * @param loginType 登录类型
     * @return 权限编码列表
     */
    @Override
    public List<String> getPermissionList(Object loginId, String loginType) {
        Long userId = parseUserId(loginId);
        return userId == null ? List.of() : getOrLoad(userId, PERMISSION_CACHE_KEY,
                authUserMapper::selectPermissionCodesByUserId);
    }

    /**
     * 获取指定登录主体的角色编码。
     *
     * @param loginId Sa-Token 登录主体
     * @param loginType 登录类型
     * @return 角色编码列表
     */
    @Override
    public List<String> getRoleList(Object loginId, String loginType) {
        Long userId = parseUserId(loginId);
        return userId == null ? List.of() : getOrLoad(userId, ROLE_CACHE_KEY, authUserMapper::selectRoleCodesByUserId);
    }

    /**
     * 失效指定用户在全部设备共享的角色和权限缓存。
     *
     * <p>用户状态、用户角色、角色权限关系变更后必须调用本方法，确保下一次鉴权重新读取数据库。</p>
     *
     * @param userId 目标用户 ID
     */
    public void invalidateAuthorizationCache(Long userId) {
        if (userId == null) {
            return;
        }
        SaSession accountSession = StpUtil.getSessionByLoginId(userId, false);
        if (accountSession != null) {
            accountSession.delete(PERMISSION_CACHE_KEY);
            accountSession.delete(ROLE_CACHE_KEY);
            accountSession.update();
        }
    }

    /**
     * 从账号 Session 读取授权缓存；未命中时查询数据库并写入所有设备共享的账号 Session。
     */
    @SuppressWarnings("unchecked")
    private List<String> getOrLoad(Long userId, String cacheKey, java.util.function.Function<Long, List<String>> loader) {
        SaSession accountSession = StpUtil.getSessionByLoginId(userId, false);
        if (accountSession != null) {
            Object cached = accountSession.get(cacheKey);
            if (cached instanceof List<?> cachedValues && cachedValues.stream().allMatch(String.class::isInstance)) {
                return (List<String>) cachedValues;
            }
        }
        List<String> values = List.copyOf(loader.apply(userId));
        if (accountSession != null) {
            accountSession.set(cacheKey, values);
            accountSession.update();
        }
        return values;
    }

    /**
     * 将 Sa-Token 登录主体转换为稳定用户ID。
     *
     * @param loginId Sa-Token 登录主体
     * @return 用户ID；格式异常时返回 null，以默认拒绝权限
     */
    private Long parseUserId(Object loginId) {
        if (loginId == null) {
            return null;
        }
        try {
            return Long.valueOf(String.valueOf(loginId));
        } catch (NumberFormatException exception) {
            // 非法登录主体不得获得任何角色或权限。
            return null;
        }
    }
}
