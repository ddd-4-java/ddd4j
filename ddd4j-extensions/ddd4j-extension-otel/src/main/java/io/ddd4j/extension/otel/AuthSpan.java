package io.ddd4j.extension.otel;

import io.ddd4j.core.auth.AuthPrincipal;
import io.ddd4j.core.auth.AuthRequest;
import io.ddd4j.core.util.SubjectKit;
import io.ddd4j.kit.lang.StrKit;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;

import java.util.Objects;

/**
 * 认证操作（Auth 集成点 #8）的 OTel Span 包装。
 *
 * <p>为 {@link SubjectKit} 的 login / logout / verify / hasRole / hasPermission 操作
 * 提供 span 包装，自动添加：
 * <ul>
 *   <li>{@code auth.framework} - 鉴权框架标识（sa-token/shiro/security/...）</li>
 *   <li>{@code auth.login_id} - 用户登录 ID</li>
 *   <li>{@code auth.success} - 鉴权是否成功</li>
 *   <li>{@code auth.operation} - 操作类型（login/logout/verify/hasRole/hasPermission）</li>
 * </ul>
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * String token = AuthSpan.login(authRequest);
 * boolean allowed = AuthSpan.hasPermission("user:delete");
 * AuthSpan.logout();
 * }</pre>
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 4.0.0
 */
public final class AuthSpan {

    /**
     * 鉴权相关标准化属性键。
     */
    public static final AttributeKey<String> ATTR_AUTH_FRAMEWORK = AttributeKey.stringKey("auth.framework");
    /** Span \u5c5e\u6027\u952e {@code auth.login_id}\u3002 */
    public static final AttributeKey<String> ATTR_AUTH_LOGIN_ID = AttributeKey.stringKey("auth.login_id");
    /** Span \u5c5e\u6027\u952e {@code auth.success}\u3002 */
    public static final AttributeKey<Boolean> ATTR_AUTH_SUCCESS = AttributeKey.booleanKey("auth.success");
    /** Span \u5c5e\u6027\u952e {@code auth.operation}\u3002 */
    public static final AttributeKey<String> ATTR_AUTH_OPERATION = AttributeKey.stringKey("auth.operation");
    /** Span \u5c5e\u6027\u952e {@code auth.role}\u3002 */
    public static final AttributeKey<String> ATTR_AUTH_ROLE = AttributeKey.stringKey("auth.role");
    /** Span \u5c5e\u6027\u952e {@code auth.permission}\u3002 */
    public static final AttributeKey<String> ATTR_AUTH_PERMISSION = AttributeKey.stringKey("auth.permission");

    /** \u8ba4\u8bc1\u8ddf\u8e2a\u5b57\u6bb5\u7f3a\u5c11\u6846\u67b6\u6807\u8bc6\u65f6\u4f7f\u7528\u7684\u9ed8\u8ba4\u540d\u79f0\u3002 */
    private static final String DEFAULT_FRAMEWORK = "ddd4j";

    /** \u9650\u5236\u8c03\u7528\u65b9\u76f4\u63a5\u521b\u5efa <code>AuthSpan</code> \u5b9e\u4f8b\u3002 */
    private AuthSpan() {
    }

    /**
     * 包装 {@link SubjectKit#login(AuthRequest)}。
     *
     * @param request 登录请求
     * @return 登录凭证（token）
     */
    public static String login(AuthRequest request) {
        if (!Ddd4jOtel.isAvailable()) {
            return SubjectKit.login(request);
        }
        Tracer tracer = Ddd4jOtel.tracer();
        Span span = tracer.spanBuilder("auth.login")
                .setSpanKind(SpanKind.INTERNAL)
                .setAttribute(ATTR_AUTH_OPERATION, "login")
                .setAttribute(ATTR_AUTH_FRAMEWORK, DEFAULT_FRAMEWORK)
                .startSpan();
        if (Objects.nonNull(request) && Objects.nonNull(request.getLoginId())) {
            span.setAttribute(ATTR_AUTH_LOGIN_ID, String.valueOf(request.getLoginId()));
        }
        try (Scope scope = span.makeCurrent()) {
            Ddd4jOtel.enrichWithBusinessContext(span);
            String token = SubjectKit.login(request);
            boolean success = StrKit.isNotEmpty(token);
            span.setAttribute(ATTR_AUTH_SUCCESS, success);
            if (!success) {
                span.setStatus(StatusCode.ERROR, "login failed");
            }
            return token;
        } catch (Throwable t) {
            span.recordException(t);
            span.setStatus(StatusCode.ERROR, t.getClass().getSimpleName());
            span.setAttribute(ATTR_AUTH_SUCCESS, false);
            throw t;
        } finally {
            span.end();
        }
    }

    /**
     * 包装 {@link SubjectKit#logout()}。
     */
    public static void logout() {
        if (!Ddd4jOtel.isAvailable()) {
            SubjectKit.logout();
            return;
        }
        Tracer tracer = Ddd4jOtel.tracer();
        Span span = tracer.spanBuilder("auth.logout")
                .setSpanKind(SpanKind.INTERNAL)
                .setAttribute(ATTR_AUTH_OPERATION, "logout")
                .setAttribute(ATTR_AUTH_FRAMEWORK, DEFAULT_FRAMEWORK)
                .startSpan();
        attachCurrentLoginId(span);
        try (Scope scope = span.makeCurrent()) {
            Ddd4jOtel.enrichWithBusinessContext(span);
            SubjectKit.logout();
            span.setAttribute(ATTR_AUTH_SUCCESS, true);
        } catch (Throwable t) {
            span.recordException(t);
            span.setStatus(StatusCode.ERROR, t.getClass().getSimpleName());
            span.setAttribute(ATTR_AUTH_SUCCESS, false);
            throw t;
        } finally {
            span.end();
        }
    }

    /**
     * \u5305\u88c5 {@link SubjectKit#logout(Object)}\uff08\u6309 loginId \u767b\u51fa\uff09\u3002
     *
     * @param loginId \u767b\u5f55\u6807\u8bc6
     */
    public static void logout(Object loginId) {
        if (!Ddd4jOtel.isAvailable()) {
            SubjectKit.logout(loginId);
            return;
        }
        Tracer tracer = Ddd4jOtel.tracer();
        Span span = tracer.spanBuilder("auth.logout")
                .setSpanKind(SpanKind.INTERNAL)
                .setAttribute(ATTR_AUTH_OPERATION, "logout")
                .setAttribute(ATTR_AUTH_FRAMEWORK, DEFAULT_FRAMEWORK)
                .setAttribute(ATTR_AUTH_LOGIN_ID, String.valueOf(loginId))
                .startSpan();
        try (Scope scope = span.makeCurrent()) {
            Ddd4jOtel.enrichWithBusinessContext(span);
            SubjectKit.logout(loginId);
            span.setAttribute(ATTR_AUTH_SUCCESS, true);
        } catch (Throwable t) {
            span.recordException(t);
            span.setStatus(StatusCode.ERROR, t.getClass().getSimpleName());
            span.setAttribute(ATTR_AUTH_SUCCESS, false);
            throw t;
        } finally {
            span.end();
        }
    }

    /**
     * \u5305\u88c5 {@link SubjectKit#verify(String)}\u3002
     *
     * @param <T> \u8fd4\u56de\u503c\u7c7b\u578b
     * @param token \u8eab\u4efd\u4ee4\u724c
     * @return \u5df2\u9a8c\u8bc1\u8eab\u4efd\u4e3b\u4f53
     */
    public static <T extends AuthPrincipal> T verify(String token) {
        if (!Ddd4jOtel.isAvailable()) {
            return SubjectKit.verify(token);
        }
        Tracer tracer = Ddd4jOtel.tracer();
        Span span = tracer.spanBuilder("auth.verify")
                .setSpanKind(SpanKind.INTERNAL)
                .setAttribute(ATTR_AUTH_OPERATION, "verify")
                .setAttribute(ATTR_AUTH_FRAMEWORK, DEFAULT_FRAMEWORK)
                .startSpan();
        try (Scope scope = span.makeCurrent()) {
            Ddd4jOtel.enrichWithBusinessContext(span);
            T principal = SubjectKit.verify(token);
            boolean success = Objects.nonNull(principal);
            span.setAttribute(ATTR_AUTH_SUCCESS, success);
            if (success && Objects.nonNull(principal.getLoginId())) {
                span.setAttribute(ATTR_AUTH_LOGIN_ID, String.valueOf(principal.getLoginId()));
            }
            if (!success) {
                span.setStatus(StatusCode.ERROR, "verify failed");
            }
            return principal;
        } catch (Throwable t) {
            span.recordException(t);
            span.setStatus(StatusCode.ERROR, t.getClass().getSimpleName());
            span.setAttribute(ATTR_AUTH_SUCCESS, false);
            throw t;
        } finally {
            span.end();
        }
    }

    /**
     * \u5305\u88c5 {@link SubjectKit#hasRole(String)}\u3002
     *
     * @param roleIdentifier \u89d2\u8272\u6807\u8bc6
     * @return \u5177\u6709\u6307\u5b9a\u89d2\u8272\u65f6\u4e3a true
     */
    public static boolean hasRole(String roleIdentifier) {
        if (!Ddd4jOtel.isAvailable()) {
            return SubjectKit.hasRole(roleIdentifier);
        }
        Tracer tracer = Ddd4jOtel.tracer();
        Span span = tracer.spanBuilder("auth.verify")
                .setSpanKind(SpanKind.INTERNAL)
                .setAttribute(ATTR_AUTH_OPERATION, "hasRole")
                .setAttribute(ATTR_AUTH_FRAMEWORK, DEFAULT_FRAMEWORK)
                .setAttribute(ATTR_AUTH_ROLE, Objects.isNull(roleIdentifier) ? "" : roleIdentifier)
                .startSpan();
        try (Scope scope = span.makeCurrent()) {
            Ddd4jOtel.enrichWithBusinessContext(span);
            boolean result = SubjectKit.hasRole(roleIdentifier);
            span.setAttribute(ATTR_AUTH_SUCCESS, result);
            return result;
        } finally {
            span.end();
        }
    }

    /**
     * \u5305\u88c5 {@link SubjectKit#hasPermission(String)}\u3002
     *
     * @param permission \u6743\u9650\u6807\u8bc6
     * @return \u5177\u6709\u6307\u5b9a\u6743\u9650\u65f6\u4e3a true
     */
    public static boolean hasPermission(String permission) {
        if (!Ddd4jOtel.isAvailable()) {
            return SubjectKit.hasPermission(permission);
        }
        Tracer tracer = Ddd4jOtel.tracer();
        Span span = tracer.spanBuilder("auth.verify")
                .setSpanKind(SpanKind.INTERNAL)
                .setAttribute(ATTR_AUTH_OPERATION, "hasPermission")
                .setAttribute(ATTR_AUTH_FRAMEWORK, DEFAULT_FRAMEWORK)
                .setAttribute(ATTR_AUTH_PERMISSION, Objects.isNull(permission) ? "" : permission)
                .startSpan();
        try (Scope scope = span.makeCurrent()) {
            Ddd4jOtel.enrichWithBusinessContext(span);
            boolean result = SubjectKit.hasPermission(permission);
            span.setAttribute(ATTR_AUTH_SUCCESS, result);
            return result;
        } finally {
            span.end();
        }
    }

    /**
     * \u5305\u88c5 {@link SubjectKit#isLogin()}\u3002
     *
     * @return \u5df2\u767b\u5f55\u65f6\u4e3a true
     */
    public static boolean isLogin() {
        if (!Ddd4jOtel.isAvailable()) {
            return SubjectKit.isLogin();
        }
        Tracer tracer = Ddd4jOtel.tracer();
        Span span = tracer.spanBuilder("auth.verify")
                .setSpanKind(SpanKind.INTERNAL)
                .setAttribute(ATTR_AUTH_OPERATION, "isLogin")
                .setAttribute(ATTR_AUTH_FRAMEWORK, DEFAULT_FRAMEWORK)
                .startSpan();
        try (Scope scope = span.makeCurrent()) {
            boolean result = SubjectKit.isLogin();
            span.setAttribute(ATTR_AUTH_SUCCESS, result);
            return result;
        } finally {
            span.end();
        }
    }

    /**
     * \u5305\u88c5 {@link SubjectKit#kickout(Object)}\u3002
     *
     * @param loginId \u767b\u5f55\u6807\u8bc6
     */
    public static void kickout(Object loginId) {
        if (!Ddd4jOtel.isAvailable()) {
            SubjectKit.kickout(loginId);
            return;
        }
        Tracer tracer = Ddd4jOtel.tracer();
        Span span = tracer.spanBuilder("auth.logout")
                .setSpanKind(SpanKind.INTERNAL)
                .setAttribute(ATTR_AUTH_OPERATION, "kickout")
                .setAttribute(ATTR_AUTH_FRAMEWORK, DEFAULT_FRAMEWORK)
                .setAttribute(ATTR_AUTH_LOGIN_ID, String.valueOf(loginId))
                .startSpan();
        try (Scope scope = span.makeCurrent()) {
            Ddd4jOtel.enrichWithBusinessContext(span);
            SubjectKit.kickout(loginId);
            span.setAttribute(ATTR_AUTH_SUCCESS, true);
        } catch (Throwable t) {
            span.recordException(t);
            span.setStatus(StatusCode.ERROR, t.getClass().getSimpleName());
            span.setAttribute(ATTR_AUTH_SUCCESS, false);
            throw t;
        } finally {
            span.end();
        }
    }

    /**
     * \u628a\u5f53\u524d\u4f1a\u8bdd\u7684\u767b\u5f55\u6807\u8bc6\u8865\u5230\u8ba4\u8bc1\u8ddf\u8e2a Span\u3002
     * @param span \u63a5\u6536\u5f53\u524d\u8ddf\u8e2a\u5c5e\u6027\u7684 Span
     */
    private static void attachCurrentLoginId(Span span) {
        try {
            AuthPrincipal principal = SubjectKit.getPrincipal();
            if (Objects.nonNull(principal) && Objects.nonNull(principal.getLoginId())) {
                span.setAttribute(ATTR_AUTH_LOGIN_ID, String.valueOf(principal.getLoginId()));
            }
        } catch (Exception ignored) {
            // 静默失败
        }
    }
}
