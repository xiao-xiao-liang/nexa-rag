package com.nexarag.auth.filter;

import com.nexarag.auth.constants.AuthConstants;
import com.nexarag.auth.context.CurrentUser;
import com.nexarag.auth.context.CurrentUserContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 在真实登录接入前，为每个 HTTP 请求注入固定的本地用户身份。
 */
public class FixedUserAuthenticationFilter extends OncePerRequestFilter {

    /**
     * 设置固定用户、执行后续过滤器并清理请求上下文。
     *
     * @param request HTTP 请求
     * @param response HTTP 响应
     * @param filterChain 过滤器链
     * @throws ServletException 过滤器链执行失败时抛出
     * @throws IOException 过滤器链执行失败时抛出
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        // 1. 写入本地固定用户身份
        CurrentUserContext.set(new CurrentUser(AuthConstants.DEFAULT_USER_ID));
        try {
            // 2. 执行后续请求处理链
            filterChain.doFilter(request, response);
        } finally {
            // 3. 清理线程上下文，避免线程复用导致身份串线
            CurrentUserContext.clear();
        }
    }
}
