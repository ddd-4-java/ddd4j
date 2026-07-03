package io.ddd4j.web.javalin.auth.shiro;

import io.javalin.Javalin;
import lombok.extern.slf4j.Slf4j;
import org.apache.shiro.authc.AuthenticationException;
import org.apache.shiro.authc.IncorrectCredentialsException;
import org.apache.shiro.authc.LockedAccountException;
import org.apache.shiro.authc.UnknownAccountException;
import org.apache.shiro.authz.AuthorizationException;
import org.apache.shiro.authz.UnauthorizedException;

import java.util.Map;

/**
 * Javalin + Shiro 异常处理注册器。
 * <p>注册 Apache Shiro 鉴权相关的各类异常处理，返回对应的 HTTP 状态码和提示信息。</p>
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@Slf4j
public final class ShiroExceptionHandlerRegistrar {

    private ShiroExceptionHandlerRegistrar() {
    }

    /**
     * 注册 Shiro 异常处理器到 Javalin 应用。
     *
     * @param app Javalin 应用实例
     */
    public static void register(Javalin app) {
        registerException(app, AuthenticationException.class, 401, "未登录或登录已过期");
        registerException(app, UnknownAccountException.class, 401, "账号不存在");
        registerException(app, IncorrectCredentialsException.class, 401, "账号或密码错误");
        registerException(app, LockedAccountException.class, 403, "账号已被锁定");
        registerException(app, UnauthorizedException.class, 403, "无权限访问");
        registerException(app, AuthorizationException.class, 403, "无权限访问");
    }

    /**
     * 注册指定异常类型的处理逻辑。
     *
     * @param app            Javalin 应用实例
     * @param exceptionClass 异常类型
     * @param status         HTTP 状态码
     * @param message        返回的提示信息
     * @param <E>            异常泛型类型
     */
    private static <E extends Exception> void registerException(
            Javalin app, Class<E> exceptionClass, int status, String message) {
        app.unsafe.routes.exception(exceptionClass, (ex, ctx) -> {
            log.warn("Shiro 鉴权异常：{} - {}", exceptionClass.getSimpleName(), ex.getMessage());
            ctx.status(status);
            ctx.json(Map.of("code", status, "msg", message));
        });
    }
}
