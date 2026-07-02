package io.ddd4j.web.javalin.web;

import com.google.inject.Inject;
import io.ddd4j.core.ApiRestResponse;
import io.ddd4j.core.i18n.I18nProvider;
import io.ddd4j.core.domain.event.DomainEventPublisher;
import io.javalin.http.Context;

/**
 * Javalin Web 控制器基类（纯净版，零 Spring 依赖）。
 *
 * <p>对标 Spring 轨道控制器基类，但基于 Javalin {@link Context}：
 * <ul>
 *   <li>响应统一返回 ddd4j-core 的 {@link ApiRestResponse}</li>
 *   <li>国际化通过 {@link I18nProvider}</li>
 *   <li>事件通过 {@link DomainEventPublisher} 发布</li>
 * </ul>
 */
public abstract class BaseController {

    @Inject
    protected I18nProvider i18nProvider;

    @Inject
    protected DomainEventPublisher eventPublisher;

    protected String message(String key, Object... args) {
        return i18nProvider.getMessage(key, args);
    }

    protected <T> ApiRestResponse<T> success() {
        return ApiRestResponse.success((T) null);
    }

    protected <T> ApiRestResponse<T> success(T data) {
        return ApiRestResponse.success(data);
    }

    protected <T> ApiRestResponse<T> success(String messageKey, Object... args) {
        return ApiRestResponse.success(message(messageKey, args));
    }

    protected <T> ApiRestResponse<T> fail(String messageKey, Object... args) {
        return ApiRestResponse.fail(message(messageKey, args));
    }

    protected <T> ApiRestResponse<T> error(String messageKey, Object... args) {
        return ApiRestResponse.error(message(messageKey, args));
    }

    protected <T> void render(Context ctx, ApiRestResponse<T> response) {
        ctx.status(io.javalin.http.HttpStatus.OK).json(response);
    }
}
