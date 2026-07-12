package com.nexarag.auth.context;

/**
 * 基于线程上下文保存当前请求用户身份。
 */
public final class CurrentUserContext {

    private static final ThreadLocal<CurrentUser> HOLDER = new ThreadLocal<>();

    private CurrentUserContext() {
    }

    /**
     * 设置当前请求用户。
     *
     * @param currentUser 当前用户
     */
    public static void set(CurrentUser currentUser) {
        if (currentUser == null) {
            throw new IllegalArgumentException("当前用户不能为空");
        }
        HOLDER.set(currentUser);
    }

    /**
     * 获取当前请求用户，不存在时抛出异常。
     *
     * @return 当前用户
     * @throws IllegalStateException 请求上下文未设置用户时抛出
     */
    public static CurrentUser getRequired() {
        CurrentUser currentUser = HOLDER.get();
        if (currentUser == null) {
            throw new IllegalStateException("当前请求未设置用户身份");
        }
        return currentUser;
    }

    /**
     * 清理当前线程中的用户身份。
     */
    public static void clear() {
        HOLDER.remove();
    }
}
