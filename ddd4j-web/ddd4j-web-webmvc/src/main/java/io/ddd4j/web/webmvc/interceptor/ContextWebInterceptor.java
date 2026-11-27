package io.ddd4j.web.webmvc.interceptor;

import io.ddd4j.core.auth.session.SessionContext;
import io.ddd4j.core.constant.ContextConstants;
import io.ddd4j.core.context.ThreadContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;

import java.util.Locale;
import java.util.Objects;

/**
 * 上下文 Web 拦截器（Servlet 实现）。
 *
 * <p><b>迁移说明</b>：自 2.0.x 起，本类将从 {@code ddd4j-web/ddd4j-web-core} 下移到
 * {@code ddd4j-web-webmvc}（Spring Boot starter）。新业务请直接依赖该 starter。
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

    /**
     * 返回拦截器执行顺序。
     *
     * @return 实际返回<code>-400</code>，供调用方取得当前值
     */
    @Override
    public int getOrder() {
        return -400;
    }

    /**
     * 在业务处理器执行前处理当前请求。
     *
     * <p>此实现调用 <code>io.ddd4j.kit.lang.StrKit.isNotEmpty(tenantId)</code>。</p>
     *
     * @param request HTTP 请求
     * @param response HTTP 响应
     * @param handler 目标处理器
     * @return 此实现返回<code>true</code>
     * @throws Exception 当前框架、存储或底层客户端无法完成此操作时
     */
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 提取租户 ID（兼容三种 header 命名）
        String tenantId = extractHeader(request, "tenant_id", "tenant-id", "tenantId");
        if (Objects.nonNull(tenantId) && io.ddd4j.kit.lang.StrKit.isNotEmpty(tenantId)) {
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
        if (Objects.nonNull(roleHeader) && io.ddd4j.kit.lang.StrKit.isNotEmpty(roleHeader)) {
            try {
                ThreadContext.set(ContextConstants.ROLE, Integer.parseInt(roleHeader));
            } catch (NumberFormatException e) {
                log.warn("Invalid role header: {}", roleHeader);
            }
        }

        // 解析语言偏好（如 "en-US,en;q=0.9" -> "en-US"）
        String acceptLanguage = request.getHeader("Accept-Language");
        if (Objects.nonNull(acceptLanguage) && io.ddd4j.kit.lang.StrKit.isNotEmpty(acceptLanguage)) {
            String lang = acceptLanguage.split(",")[0].replace('-', '_');
            ThreadContext.set(ContextConstants.LOCALE, Locale.forLanguageTag(lang));
        }

        // 会话解析（子类可重写）
        String thirdSession = request.getHeader("third_session");
        if (Objects.nonNull(thirdSession) && io.ddd4j.kit.lang.StrKit.isNotEmpty(thirdSession)) {
            try {
                SessionContext sessionContext = resolveSession(request);
                if (Objects.nonNull(sessionContext)) {
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

    /**
     * 处理请求完成后的资源释放或上下文清理。
     *
     * <p>此实现调用 <code>ThreadContext.clear()</code>。</p>
     *
     * @param request HTTP 请求
     * @param response HTTP 响应
     * @param handler 目标处理器
     * @param ex 捕获的异常
     * @throws Exception 当前框架、存储或底层客户端无法完成此操作时
     */
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
     * \u4ece\u8bf7\u6c42\u5934\u4e2d\u63d0\u53d6\u503c\uff0c\u652f\u6301\u591a\u4e2a header \u540d\u79f0\uff08\u6309\u4f18\u5148\u7ea7\uff09\u3002
     *
     * @param request \u5f53\u524d Web \u8bf7\u6c42
     * @param names \u6309\u987a\u5e8f\u5c1d\u8bd5\u7684\u8bf7\u6c42\u5934\u540d\u79f0
     * @return \u9996\u4e2a\u53ef\u7528\u7684\u8bf7\u6c42\u5934\u503c
     */
    private String extractHeader(HttpServletRequest request, String... names) {
        for (String name : names) {
            String value = request.getHeader(name);
            if (Objects.nonNull(value) && io.ddd4j.kit.lang.StrKit.isNotEmpty(value)) {
                return value;
            }
        }
        return null;
    }
}
