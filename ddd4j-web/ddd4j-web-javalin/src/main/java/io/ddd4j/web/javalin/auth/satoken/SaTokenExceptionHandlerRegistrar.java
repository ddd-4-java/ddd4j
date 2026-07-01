package io.ddd4j.web.javalin.auth.satoken;

import cn.dev33.satoken.exception.SaTokenException;
import io.javalin.Javalin;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.Objects;

/**
 * Javalin + Sa-Token 异常处理注册器。
 */
@Slf4j
public final class SaTokenExceptionHandlerRegistrar {

    private SaTokenExceptionHandlerRegistrar() {
    }

    public static void register(Javalin app) {
        app.unsafe.routes.exception(SaTokenException.class, (ex, ctx) -> {
            log.warn("Sa-Token 鉴权异常：code={}, msg={}", ex.getCode(), ex.getMessage());
            ctx.status(401);
            ctx.json(Map.of(
                    "code", ex.getCode(),
                    "msg", Objects.isNull(ex.getMessage()) ? "" : ex.getMessage()
            ));
        });
    }
}
