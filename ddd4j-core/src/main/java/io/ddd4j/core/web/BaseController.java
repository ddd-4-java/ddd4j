/**
 * Copyright (C) 2018 Hiwepy (http://hiwepy.io).
 * All Rights Reserved.
 */
package io.ddd4j.core.web;

import io.ddd4j.core.ApiRestResponse;
import io.ddd4j.core.exception.PayloadExceptionEvent;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import lombok.Getter;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.biz.context.NestedMessageSource;
import org.springframework.context.*;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.util.StringValueResolver;

@ApiResponses({
        @ApiResponse(responseCode = "400", description = "参数类型不匹配或格式不正确", content = @Content(schema = @Schema(implementation = ApiRestResponse.class))),
        @ApiResponse(responseCode = "401", description = "不允许访问（功能未授权）", content = @Content(schema = @Schema(implementation = ApiRestResponse.class))),
        @ApiResponse(responseCode = "403", description = "服务器拒绝请求", content = @Content(schema = @Schema(implementation = ApiRestResponse.class))),
        @ApiResponse(responseCode = "404", description = "请求地址不存在", content = @Content(schema = @Schema(implementation = ApiRestResponse.class))),
        @ApiResponse(responseCode = "405", description = "不支持的请求方法", content = @Content(schema = @Schema(implementation = ApiRestResponse.class))),
        @ApiResponse(responseCode = "406", description = "不匹配的媒体类型", content = @Content(schema = @Schema(implementation = ApiRestResponse.class))),
        @ApiResponse(responseCode = "415", description = "不支持的媒体类型", content = @Content(schema = @Schema(implementation = ApiRestResponse.class))),
        @ApiResponse(responseCode = "413", description = "请求实体过大", content = @Content(schema = @Schema(implementation = ApiRestResponse.class))),
        @ApiResponse(responseCode = "500", description = "服务器内部错误", content = @Content(schema = @Schema(implementation = ApiRestResponse.class))),
        @ApiResponse(responseCode = "502", description = "错误网关", content = @Content(schema = @Schema(implementation = ApiRestResponse.class))),
        @ApiResponse(responseCode = "503", description = "服务不可用", content = @Content(schema = @Schema(implementation = ApiRestResponse.class))),
        @ApiResponse(responseCode = "504", description = "网关访问超时", content = @Content(schema = @Schema(implementation = ApiRestResponse.class)))
})
public class BaseController implements ApplicationEventPublisherAware, ApplicationContextAware, EmbeddedValueResolverAware {

    @Getter
    private StringValueResolver valueResolver;
    @Getter
    private ApplicationEventPublisher eventPublisher;
    @Getter
    private ApplicationContext context;
    @Autowired(required = false)
    @Getter
    private NestedMessageSource messageSource;

    /**
     * 统一处理异常，并抛出异常事件方便进行统一的日志实现
     */
    protected void logException(Object source, Exception ex) {
        getEventPublisher().publishEvent(new PayloadExceptionEvent(source, ex));
    }

    /**
     * 获取国际化信息
     *
     * @param key  国际化Key
     * @param args 参数
     * @return 国际化字符串
     */
    protected String getMessage(String key, Object... args) {
        return getMessageSource().getMessage(key, args, LocaleContextHolder.getLocale());
    }

    protected <T> ApiRestResponse<T> success(String key, Object... args) {
        return ApiRestResponse.success(getMessage(key, args));
    }

    protected <T> ApiRestResponse<T> fail(String key, Object... args) {
        return ApiRestResponse.fail(getMessage(key, args));
    }

    protected <T> ApiRestResponse<T> error(String key, Object... args) {
        return ApiRestResponse.error(getMessage(key, args));
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.context = applicationContext;
    }

    @Override
    public void setApplicationEventPublisher(ApplicationEventPublisher applicationEventPublisher) {
        this.eventPublisher = applicationEventPublisher;
    }

    @Override
    public void setEmbeddedValueResolver(StringValueResolver resolver) {
        this.valueResolver = resolver;
    }

}
