package io.ddd4j.extension.otel;

import io.ddd4j.core.auth.AuthPrincipal;
import io.ddd4j.core.auth.AuthRequest;
import io.ddd4j.core.util.SubjectKit;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;

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

    /** 鉴权相关标准化属性键。 */
    public static final AttributeKey<String> ATTR_AUTH_FRAMEWORK = AttributeKey.stringKey("auth.framework");
    public static final AttributeKey<String> ATTR_AUTH_LOGIN_ID = AttributeKey.stringKey("auth.login_id");
    public static final AttributeKey<Boolean> ATTR_AUTH_SUCCESS = AttributeKey.booleanKey("auth.success");
    public static final AttributeKey<String> ATTR_AUTH_OPERATION = AttributeKey.stringKey("auth.operation");
    public static final AttributeKey<String> ATTR_AUTH_ROLE = AttributeKey.stringKey("auth.role");
    public static final AttributeKey<String> ATTR_AUTH_PERMISSION = AttributeKey.stringKey("auth.permission");

    private static final String DEFAULT_FRAMEWORK = "ddd4j";

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
        if (request != null && request.getLoginId() != null) {
            span.setAttribute(ATTR_AUTH_LOGIN_ID, String.valueOf(request.getLoginId()));
        }
        try (Scope scope = span.makeCurrent()) {
            Ddd4jOtel.enrichWithBusinessContext(span);
            String token = SubjectKit.login(request);
            boolean success = token != null && !token.isEmpty();
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
     * 包装 {@link SubjectKit#logout(Object)}（按 loginId 登出）。
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
     * 包装 {@link SubjectKit#verify(String)}。
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
            boolean success = principal != null;
            span.setAttribute(ATTR_AUTH_SUCCESS, success);
            if (success && principal.getLoginId() != null) {
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
     * 包装 {@link SubjectKit#hasRole(String)}。
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
                .setAttribute(ATTR_AUTH_ROLE, roleIdentifier == null ? "" : roleIdentifier)
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
     * 包装 {@link SubjectKit#hasPermission(String)}。
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
                .setAttribute(ATTR_AUTH_PERMISSION, permission == null ? "" : permission)
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
     * 包装 {@link SubjectKit#isLogin()}。
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
     * 包装 {@link SubjectKit#kickout(Object)}。
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

    private static void attachCurrentLoginId(Span span) {
        try {
            AuthPrincipal principal = SubjectKit.getPrincipal();
            if (principal != null && principal.getLoginId() != null) {
                span.setAttribute(ATTR_AUTH_LOGIN_ID, String.valueOf(principal.getLoginId()));
            }
        } catch (Exception ignored) {
            // 静默失败
        }
    }
}