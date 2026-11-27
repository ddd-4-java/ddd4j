/*
 * Copyright (c) 2024-2026 ddd4j project. All rights reserved.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.ddd4j.web.webmvc.controller;

import com.github.dozermapper.core.Mapper;
import io.ddd4j.core.api.R;
import io.ddd4j.core.api.ApiCode;
import io.ddd4j.spring.event.AppExceptionEvent;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import lombok.Getter;
import org.springframework.beans.BeansException;
import org.springframework.context.*;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.extension.context.NestedMessageSource;
import org.springframework.util.StringValueResolver;

/**
 * Spring MVC 基础控制器。
 * <p>
 * 提供国际化消息获取、异常事件发布、API 响应封装等基础能力。
 * 业务 Controller 应继承此类以获得统一的基础能力。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@ApiResponses({
        @ApiResponse(responseCode = "400", description = "参数类型不匹配或格式不正确", content = @Content(schema = @Schema(implementation = R.class))),
        @ApiResponse(responseCode = "401", description = "不允许访问（功能未授权）", content = @Content(schema = @Schema(implementation = R.class))),
        @ApiResponse(responseCode = "403", description = "服务器拒绝请求", content = @Content(schema = @Schema(implementation = R.class))),
        @ApiResponse(responseCode = "404", description = "请求地址不存在", content = @Content(schema = @Schema(implementation = R.class))),
        @ApiResponse(responseCode = "405", description = "不支持的请求方法", content = @Content(schema = @Schema(implementation = R.class))),
        @ApiResponse(responseCode = "406", description = "不匹配的媒体类型", content = @Content(schema = @Schema(implementation = R.class))),
        @ApiResponse(responseCode = "415", description = "不支持的媒体类型", content = @Content(schema = @Schema(implementation = R.class))),
        @ApiResponse(responseCode = "413", description = "请求实体过大", content = @Content(schema = @Schema(implementation = R.class))),
        @ApiResponse(responseCode = "500", description = "服务器内部错误", content = @Content(schema = @Schema(implementation = R.class))),
        @ApiResponse(responseCode = "502", description = "错误网关", content = @Content(schema = @Schema(implementation = R.class))),
        @ApiResponse(responseCode = "503", description = "服务不可用", content = @Content(schema = @Schema(implementation = R.class))),
        @ApiResponse(responseCode = "504", description = "网关访问超时", content = @Content(schema = @Schema(implementation = R.class)))
})
public class BaseController implements ApplicationEventPublisherAware, ApplicationContextAware, EmbeddedValueResolverAware {

    /**
     * 嵌套消息源（国际化）
     */
    @Getter
    private final NestedMessageSource messageSource;
    /**
     * Dozer Bean 映射器
     */
    @Getter
    private final Mapper beanMapper;
    /**
     * 嵌入式值解析器
     */
    @Getter
    private StringValueResolver valueResolver;
    /**
     * 应用事件发布器
     */
    @Getter
    private ApplicationEventPublisher eventPublisher;
    /**
     * Spring 应用上下文
     */
    @Getter
    private ApplicationContext context;

    /**
     * \u521b\u5efa BaseController \u5b9e\u4f8b\u3002
     * @param messageSource \u6d88\u606f\u6e90
     * @param beanMapper Bean \u6620\u5c04\u5668
     */
    public BaseController(NestedMessageSource messageSource, Mapper beanMapper) {
        this.messageSource = messageSource;
        this.beanMapper = beanMapper;
    }

    /**
     * 统一处理异常，并抛出异常事件方便进行统一的日志实现
     */
    /**
     * 统一处理异常，并抛出异常事件方便进行统一的日志实现
     * @param source 来源
     * @param ex 异常对象
     */

    protected void logException(Object source, Exception ex) {
        getEventPublisher().publishEvent(new AppExceptionEvent(source, ex));
    }

    /**
     * 获取国际化信息
     *
     * @param key  国际化Key
     * @param args 参数
     * @return 国际化字符串
     */
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

    /**
     * \u521b\u5efa\u6210\u529f\u54cd\u5e94\u3002
     * @param <T> \u8fd4\u56de\u6216\u8bf7\u6c42\u7684\u7c7b\u578b
     * @param key \u6d88\u606f\u952e
     * @param args \u6d88\u606f\u53c2\u6570
     * @return \u6210\u529f\u54cd\u5e94
     */
    protected <T> R<T> success(String key, Object... args) {
        return R.ok(getMessage(key, args), null);
    }

    /**
     * \u521b\u5efa\u5931\u8d25\u54cd\u5e94\u3002
     * @param <T> \u8fd4\u56de\u6216\u8bf7\u6c42\u7684\u7c7b\u578b
     * @param key \u6d88\u606f\u952e
     * @param args \u6d88\u606f\u53c2\u6570
     * @return \u5931\u8d25\u54cd\u5e94
     */
    protected <T> R<T> fail(String key, Object... args) {
        return R.fail(getMessage(key, args));
    }

    /**
     * \u521b\u5efa\u9519\u8bef\u54cd\u5e94\u3002
     * @param <T> \u8fd4\u56de\u6216\u8bf7\u6c42\u7684\u7c7b\u578b
     * @param key \u6d88\u606f\u952e
     * @param args \u6d88\u606f\u53c2\u6570
     * @return \u9519\u8bef\u54cd\u5e94
     */
    protected <T> R<T> error(String key, Object... args) {
        return R.of(ApiCode.SERVER_ERROR.getCode(), getMessage(key, args));
    }

    /**
     * \u4fdd\u5b58 Spring \u63d0\u4f9b\u7684\u5bb9\u5668\u4e0a\u4e0b\u6587\u4f9b\u63a7\u5236\u5668\u67e5\u8be2\u7ec4\u4ef6\u3002
     * @param applicationContext Spring \u5bb9\u5668\u63d0\u4f9b\u7684\u5e94\u7528\u4e0a\u4e0b\u6587
     * @throws BeansException \u5f53\u524d\u6846\u67b6\u8bf7\u6c42\u5904\u7406\u3001\u6d88\u606f\u8bfb\u5199\u6216\u53c2\u6570\u8f6c\u6362\u5931\u8d25
     */
    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.context = applicationContext;
    }

    /**
     * \u8bbe\u7f6e\u5e94\u7528\u4e8b\u4ef6\u53d1\u5e03\u5668\u3002
     * @param applicationEventPublisher \u8981\u5199\u5165\u7684\u503c
     */
    @Override
    public void setApplicationEventPublisher(ApplicationEventPublisher applicationEventPublisher) {
        this.eventPublisher = applicationEventPublisher;
    }

    /**
     * \u8bbe\u7f6e\u5d4c\u5165\u5f0f\u503c\u89e3\u6790\u5668\u3002
     * @param resolver \u8981\u5199\u5165\u7684\u503c
     */
    @Override
    public void setEmbeddedValueResolver(StringValueResolver resolver) {
        this.valueResolver = resolver;
    }

}
