package io.ddd4j.web.javalin.auth.security;

import io.javalin.Javalin;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AccountExpiredException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.AuthenticationException;

import java.util.Map;

/**
 * Javalin + Spring Security 异常处理注册器。
 */
@Slf4j
public final class SecurityExceptionHandlerRegistrar {

    private SecurityExceptionHandlerRegistrar() {
    }

    public static void register(Javalin app) {
        registerException(app, AuthenticationException.class, 401, "未登录或登录已过期");
        registerException(app, BadCredentialsException.class, 401, "账号或密码错误");
        registerException(app, AccountExpiredException.class, 401, "账号已过期");
        registerException(app, LockedException.class, 403, "账号已被锁定");
        registerException(app, DisabledException.class, 403, "账号已被禁用");
        registerException(app, AccessDeniedException.class, 403, "无权限访问");
    }

    private static <E extends Exception> void registerException(
            Javalin app, Class<E> exceptionClass, int status, String message) {
        app.unsafe.routes.exception(exceptionClass, (ex, ctx) -> {
            log.warn("Spring Security 鉴权异常：{} - {}", exceptionClass.getSimpleName(), ex.getMessage());
            ctx.status(status);
            ctx.json(Map.of("code", status, "msg", message));
        });
    }
}
