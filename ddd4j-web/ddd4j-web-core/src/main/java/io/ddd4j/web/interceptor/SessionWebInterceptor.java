package io.ddd4j.web.interceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;

/**
 * 会话拦截器（占位 - RedisKit 已移除，待重构）。
 *
 * <p><b>迁移说明</b>：自 2.0.x 起，本类将从 {@code ddd4j-web/ddd4j-web-core} 下移到
 * {@code ddd4j-web-webmvc}（Spring Boot starter）。新业务请直接依赖该 starter。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @deprecated 自 2.0.x 起下移到 {@code ddd4j-web-webmvc.SessionWebInterceptor}
 */
@Deprecated
@Slf4j
public class SessionWebInterceptor implements WebInterceptor {
    @Override
    public int getOrder() {
        return 0;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        return true;
    }
}
