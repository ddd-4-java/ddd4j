package io.ddd4j.web.javalin.handler;

import io.ddd4j.core.exception.AccountDisabledException;
import io.ddd4j.core.exception.AccountLockedException;
import io.ddd4j.core.exception.AuthenticationException;
import io.ddd4j.core.exception.AuthorizationException;
import io.ddd4j.core.exception.BadCredentialsException;
import io.ddd4j.core.exception.NotLoggedInException;
import io.ddd4j.core.exception.SessionExpiredException;
import io.ddd4j.core.exception.TokenExpiredException;
import io.ddd4j.core.exception.TokenInvalidException;
import io.ddd4j.core.exception.UnknownAccountException;
import io.javalin.Javalin;
import io.javalin.http.HttpStatus;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;

/**
 * Javalin 统一鉴权异常处理注册器（ddd4j 通用异常族 → 统一 HTTP 语义）。
 *
 * <p>三鉴权框架（SaToken / Shiro / Spring Security）的 {@code Subject} 实现都把 framework 原生异常
 * 翻译成 ddd4j 通用异常族（{@link AuthenticationException}/{@link AuthorizationException} 子类）。
 * 本类只需注册 ddd4j 通用异常 → HTTP 状态码 的统一映射，业务方无需关心底层鉴权框架。
 *
 * <h3>HTTP 状态码映射</h3>
 * <table border="1">
 *   <caption>ddd4j 通用异常 → HTTP</caption>
 *   <tr><th>ddd4j 异常</th><th>HTTP</th><th>语义</th></tr>
 *   <tr><td>{@link NotLoggedInException} / {@link AuthenticationException} / {@link TokenInvalidException} / {@link BadCredentialsException} / {@link UnknownAccountException} / {@link TokenExpiredException} / {@link SessionExpiredException}</td><td>401</td><td>认证失败</td></tr>
 *   <tr><td>{@link AccountDisabledException} / {@link AccountLockedException}</td><td>403</td><td>账号被禁</td></tr>
 *   <tr><td>{@link AuthorizationException}</td><td>403</td><td>无权限</td></tr>
 * </table>
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 3.0.0
 */
@Slf4j
public final class AuthExceptionHandlerRegistrar {

    private AuthExceptionHandlerRegistrar() {
    }

    /**
     * 注册 ddd4j 通用鉴权异常 → Javalin 响应处理。
     *
     * @param app Javalin 应用实例
     */
    public static void register(Javalin app) {
        // 401 — 认证族（这些异常统一返回 401）
        register(app, NotLoggedInException.class, HttpStatus.UNAUTHORIZED, "未登录或登录已过期");
        register(app, AuthenticationException.class, HttpStatus.UNAUTHORIZED, "认证失败");
        register(app, TokenInvalidException.class, HttpStatus.UNAUTHORIZED, "凭证无效");
        register(app, TokenExpiredException.class, HttpStatus.UNAUTHORIZED, "凭证已过期");
        register(app, SessionExpiredException.class, HttpStatus.UNAUTHORIZED, "会话已过期");
        register(app, UnknownAccountException.class, HttpStatus.UNAUTHORIZED, "账号不存在");
        register(app, BadCredentialsException.class, HttpStatus.UNAUTHORIZED, "账号或密码错误");

        // 403 — 授权族
        register(app, AccountDisabledException.class, HttpStatus.FORBIDDEN, "账号已被禁用");
        register(app, AccountLockedException.class, HttpStatus.FORBIDDEN, "账号已被锁定");
        register(app, AuthorizationException.class, HttpStatus.FORBIDDEN, "无权限访问");
    }

    private static <E extends Exception> void register(
            Javalin app, Class<E> exceptionClass, HttpStatus status, String message) {
        app.unsafe.routes.exception(exceptionClass, (ex, ctx) -> {
            log.warn("ddd4j 鉴权异常：{} - {}", exceptionClass.getSimpleName(), ex.getMessage());
            ctx.status(status);
            ctx.json(Map.of("code", status.getCode(), "msg", message));
        });
    }
}