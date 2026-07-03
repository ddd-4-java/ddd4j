package io.ddd4j.web.javalin.web;

import com.google.inject.Inject;
import io.ddd4j.core.ApiRestResponse;
import io.ddd4j.core.i18n.I18nProvider;
import io.ddd4j.core.ddd.event.DomainEventPublisher;
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

    /**
     * 国际化消息提供者
     */
    @Inject
    protected I18nProvider i18nProvider;

    /**
     * 领域事件发布器
     */
    @Inject
    protected DomainEventPublisher eventPublisher;

    /**
     * 获取国际化消息。
     *
     * @param key  消息键
     * @param args 消息参数
     * @return 国际化后的消息文本
     */
    protected String message(String key, Object... args) {
        return i18nProvider.getMessage(key, args);
    }

    /**
     * 返回成功响应（无数据）。
     *
     * @param <T> 数据类型
     * @return 成功响应
     */
    protected <T> ApiRestResponse<T> success() {
        return ApiRestResponse.success((T) null);
    }

    /**
     * 返回成功响应（带数据）。
     *
     * @param data 响应数据
     * @param <T>  数据类型
     * @return 成功响应
     */
    protected <T> ApiRestResponse<T> success(T data) {
        return ApiRestResponse.success(data);
    }

    /**
     * 返回成功响应（带国际化消息）。
     *
     * @param messageKey 国际化消息键
     * @param args       消息参数
     * @param <T>        数据类型
     * @return 成功响应
     */
    protected <T> ApiRestResponse<T> success(String messageKey, Object... args) {
        return ApiRestResponse.success(message(messageKey, args));
    }

    /**
     * 返回失败响应。
     *
     * @param messageKey 国际化消息键
     * @param args       消息参数
     * @param <T>        数据类型
     * @return 失败响应
     */
    protected <T> ApiRestResponse<T> fail(String messageKey, Object... args) {
        return ApiRestResponse.fail(message(messageKey, args));
    }

    /**
     * 返回错误响应。
     *
     * @param messageKey 国际化消息键
     * @param args       消息参数
     * @param <T>        数据类型
     * @return 错误响应
     */
    protected <T> ApiRestResponse<T> error(String messageKey, Object... args) {
        return ApiRestResponse.error(message(messageKey, args));
    }

    /**
     * 渲染响应到 Javalin 上下文。
     *
     * @param ctx      Javalin HTTP 上下文
     * @param response API 响应对象
     * @param <T>      数据类型
     */
    protected <T> void render(Context ctx, ApiRestResponse<T> response) {
        ctx.status(io.javalin.http.HttpStatus.OK).json(response);
    }
}
