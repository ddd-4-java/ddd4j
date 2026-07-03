package io.ddd4j.web.javalin.auth.satoken;

import cn.dev33.satoken.exception.SaTokenException;
import io.javalin.Javalin;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.Objects;

/**
 * Javalin + Sa-Token 异常处理注册器。
 * <p>注册 Sa-Token 鉴权异常的统一处理逻辑，返回 401 状态码和异常信息。</p>
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@Slf4j
public final class SaTokenExceptionHandlerRegistrar {

    private SaTokenExceptionHandlerRegistrar() {
    }

    /**
     * 注册 Sa-Token 异常处理器到 Javalin 应用。
     *
     * @param app Javalin 应用实例
     */
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
