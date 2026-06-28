package io.ddd4j.web.interceptor;

import io.ddd4j.core.context.ThreadContext;
import io.ddd4j.core.contract.constant.ContextConstants;
import io.ddd4j.web.core.SessionContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;

import java.util.Locale;

/**
 * 上下文 Web 拦截器。
 *
 * <p>从请求头提取上下文信息，存入 {@link ThreadContext} 以便下游使用：
 * <ul>
 *   <li>{@code tenant_id} / {@code tenant-id} / {@code tenantId} → 租户 ID</li>
 *   <li>{@code systemId} → 系统 ID</li>
 *   <li>{@code third_session} → 第三方会话标识</li>
 *   <li>{@code clientType} → 客户端类型</li>
 *   <li>{@code appId} → 应用 ID</li>
 *   <li>{@code shopId} → 店铺 ID</li>
 *   <li>{@code role} → 角色</li>
 *   <li>{@code Authorization} → 授权令牌</li>
 *   <li>{@code Accept-Language} → 语言偏好</li>
 * </ul>
 *
 * <p>会话解析：如果请求携带 {@code third_session} 头，子类可重写 {@link #resolveSession(HttpServletRequest)} 方法
 * 从 Redis 或其他存储中解析 {@link SessionContext}。
 *
 * <p>请求结束时通过 {@link #afterCompletion} 清理 ThreadContext。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @see ThreadContext
 * @see ContextConstants
 */
@Slf4j
public class ContextWebInterceptor implements WebInterceptor {

    @Override
    public int getOrder() {
        return -400;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 提取租户 ID（兼容三种 header 命名）
        String tenantId = extractHeader(request, "tenant_id", "tenant-id", "tenantId");
        if (tenantId != null && !tenantId.isEmpty()) {
            ThreadContext.set(ContextConstants.TENANT_ID, tenantId);
        }

        // 提取其他上下文头
        ThreadContext.set(ContextConstants.SYSTEM_ID, request.getHeader("systemId"));
        ThreadContext.set(ContextConstants.THIRD_SESSION, request.getHeader("third_session"));
        ThreadContext.set(ContextConstants.CLIENT_TYPE, request.getHeader("clientType"));
        ThreadContext.set(ContextConstants.APP_ID, request.getHeader("appId"));
        ThreadContext.set(ContextConstants.SHOP_ID, request.getHeader("shopId"));
        ThreadContext.set(ContextConstants.AUTHORIZATION, request.getHeader("Authorization"));

        // 提取角色（整数）
        String roleHeader = request.getHeader("role");
        if (roleHeader != null && !roleHeader.isEmpty()) {
            try {
                ThreadContext.set(ContextConstants.ROLE, Integer.parseInt(roleHeader));
            } catch (NumberFormatException e) {
                log.warn("Invalid role header: {}", roleHeader);
            }
        }

        // 解析语言偏好（如 "en-US,en;q=0.9" -> "en-US"）
        String acceptLanguage = request.getHeader("Accept-Language");
        if (acceptLanguage != null && !acceptLanguage.isEmpty()) {
            String lang = acceptLanguage.split(",")[0].replace('-', '_');
            ThreadContext.set(ContextConstants.LOCALE, Locale.forLanguageTag(lang));
        }

        // 会话解析（子类可重写）
        String thirdSession = request.getHeader("third_session");
        if (thirdSession != null && !thirdSession.isEmpty()) {
            try {
                SessionContext sessionContext = resolveSession(request);
                if (sessionContext != null) {
                    ThreadContext.set(ContextConstants.SESSION, sessionContext);
                    ThreadContext.set(ContextConstants.USER_ID, sessionContext.getUserId());
                    log.debug("Session resolved, userId={}", sessionContext.getUserId());
                } else {
                    log.warn("Session not found for third_session={}", thirdSession);
                }
            } catch (Exception e) {
                log.warn("Failed to resolve session for third_session={}", thirdSession, e);
            }
        }

        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        ThreadContext.clear();
    }

    /**
     * 从请求中解析会话上下文。子类可重写此方法以从 Redis 或其他存储中获取 SessionContext。
     *
     * <p>默认实现返回 {@code null}（不解析会话）。
     * 重写示例：
     * <pre>
     * protected SessionContext resolveSession(HttpServletRequest request) {
     *     String thirdSession = request.getHeader("third_session");
     *     return RedisKit.get("app:3rd_session:" + thirdSession, SessionContext.class);
     * }
     * </pre>
     *
     * @param request HTTP 请求
     * @return 会话上下文，未找到时返回 null
     */
    protected SessionContext resolveSession(HttpServletRequest request) {
        return null;
    }

    /**
     * 从请求头中提取值，支持多个 header 名称（按优先级）。
     */
    private String extractHeader(HttpServletRequest request, String... names) {
        for (String name : names) {
            String value = request.getHeader(name);
            if (value != null && !value.isEmpty()) {
                return value;
            }
        }
        return null;
    }
}
