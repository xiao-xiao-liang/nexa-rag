package com.nexarag.auth.mapper;

import org.junit.jupiter.api.Test;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 身份领域 Mapper 的 SQL 契约测试。
 */
class AuthUserMapperTest {

    /**
     * 验证用户账号名、权限和租户成员锁定查询具备明确的数据访问边界。
     *
     * @throws Exception Mapper 类型或注解不符合约定时抛出
     */
    @Test
    void shouldDefineUserPermissionAndTenantMembershipQueries() throws Exception {
        // 1. 验证 V24 中每个表都有对应的 DO 与 Mapper。
        assertRequiredType("com.nexarag.auth.model.dataobject.AuthUserDO");
        assertRequiredType("com.nexarag.auth.model.dataobject.AuthRoleDO");
        assertRequiredType("com.nexarag.auth.model.dataobject.AuthPermissionDO");
        assertRequiredType("com.nexarag.auth.model.dataobject.AuthRolePermissionDO");
        assertRequiredType("com.nexarag.auth.model.dataobject.TenantDO");
        assertRequiredType("com.nexarag.auth.model.dataobject.TenantMemberDO");
        Class<?> authUserMapper = assertRequiredType("com.nexarag.auth.mapper.AuthUserMapper");
        Class<?> tenantMemberMapper = assertRequiredType("com.nexarag.auth.mapper.TenantMemberMapper");

        // 2. 验证账号名、权限和成员资格查询均由 Mapper 固定 SQL 实现。
        assertThat(selectSql(authUserMapper, "selectByAccountNameKey"))
                .contains("FROM auth_user", "account_name_key");
        assertThat(selectSql(authUserMapper, "selectByUserIdForUpdate"))
                .contains("FROM auth_user", "FOR UPDATE");
        assertThat(selectSql(authUserMapper, "selectPermissionCodesByUserId"))
                .contains("auth_role_permission", "auth_permission");
        assertThat(selectSql(tenantMemberMapper, "selectActiveByTenantIdAndUserIdForUpdate"))
                .contains("FROM tenant_member", "member_status = 0", "FOR UPDATE");
    }

    /**
     * 断言指定类型存在。
     *
     * @param className 全限定类名
     * @return 已加载类型
     * @throws ClassNotFoundException 类型不存在时抛出
     */
    private Class<?> assertRequiredType(String className) throws ClassNotFoundException {
        return Class.forName(className);
    }

    /**
     * 提取 Mapper 方法上的 Select SQL。
     *
     * @param mapperType Mapper 类型
     * @param methodName 方法名
     * @return Select 注解中的 SQL
     * @throws ReflectiveOperationException Mapper 方法或注解不存在时抛出
     */
    private String selectSql(Class<?> mapperType, String methodName) throws ReflectiveOperationException {
        Method method = Arrays.stream(mapperType.getDeclaredMethods())
                .filter(candidate -> candidate.getName().equals(methodName))
                .findFirst()
                .orElseThrow(() -> new NoSuchMethodException(methodName));
        Annotation select = Arrays.stream(method.getAnnotations())
                .filter(annotation -> annotation.annotationType().getName().equals("org.apache.ibatis.annotations.Select"))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("缺少 @Select 注解：" + methodName));
        String[] statements = (String[]) select.annotationType().getMethod("value").invoke(select);
        return String.join("\n", statements);
    }
}
