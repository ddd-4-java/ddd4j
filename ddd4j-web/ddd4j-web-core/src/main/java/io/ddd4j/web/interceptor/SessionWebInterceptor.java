package io.ddd4j.web.interceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;

/**
 * 会话拦截器（占位 - RedisKit 已移除，待重构）
 */
@Slf4j
public class SessionWebInterceptor implements WebInterceptor {
    @Override
    public int getOrder() { return 0; }
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        return true;
    }
}
