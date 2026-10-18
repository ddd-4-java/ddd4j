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
package io.ddd4j.web.error;

import io.ddd4j.core.api.R;
import io.ddd4j.kit.lang.StrKit;
import io.ddd4j.web.core.error.DefaultWebExceptionTranslator;
import io.ddd4j.web.core.error.WebError;
import io.ddd4j.web.core.error.WebExceptionTranslator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

/**
 * 各 Web 框架统一异常处理的抽象基类（模板方法）。
 *
 * <p>收敛各框架适配器重复的异常处理管线：
 * <ol>
 *     <li>持有 {@link WebExceptionTranslator}（默认 {@link DefaultWebExceptionTranslator}），
 *         将 {@code BizRuntimeException} 等通用异常翻译为带 HTTP 状态的 {@link WebError}；</li>
 *     <li>翻译结果经 {@link WebErrorResponseBuilder} 统一转换为 {@link R} 响应体；</li>
 *     <li>status &gt;= 500 时按未处理错误记录 error 日志。</li>
 * </ol>
 *
 * <p>子类只保留框架特定逻辑：响应写出方式（Spring {@code ResponseEntity}、JAX-RS
 * {@code Response}、Micronaut {@code HttpResponse} 等）与框架特定异常的预处理
 * （覆盖 {@link #doTranslate(Throwable)}）。
 */
public abstract class BaseErrorConfiguration {

    private static final Logger LOG = LoggerFactory.getLogger(BaseErrorConfiguration.class);

    private final WebExceptionTranslator translator;
    private final WebErrorResponseBuilder responseBuilder;

    /** 使用默认异常翻译器和响应构造器。 */
    protected BaseErrorConfiguration() {
        this(new DefaultWebExceptionTranslator());
    }

    /**
     * 使用指定异常翻译器和默认响应构造器。
     *
     * @param translator 异常翻译器
     */
    protected BaseErrorConfiguration(WebExceptionTranslator translator) {
        this(translator, WebErrorResponseBuilder.defaults());
    }

    /**
     * 使用指定异常翻译器和响应构造器。
     *
     * @param translator 异常翻译器
     * @param responseBuilder 错误响应构造器
     */
    protected BaseErrorConfiguration(WebExceptionTranslator translator, WebErrorResponseBuilder responseBuilder) {
        this.translator = Objects.requireNonNull(translator, "translator must not be null");
        this.responseBuilder = Objects.requireNonNull(responseBuilder, "responseBuilder must not be null");
    }

    /**
     * 框架已给出 HTTP 状态时的兜底错误表示：消息为空时回退到状态短语。
     *
     * @param status HTTP 状态码
     * @param fallbackMessage 默认错误消息
     * @param message 错误消息
     * @return HTTP 状态错误
     */
    protected static WebError httpStatusError(int status, String fallbackMessage, String message) {
        return new WebError(status, status, StrKit.isBlank(message) ? fallbackMessage : message, null);
    }

    /**
     * 统一异常翻译入口：框架特定异常归一后交给 {@link WebExceptionTranslator} 翻译。
     *
     * @param throwable 原始异常
     * @return 转换后的 Web 错误
     */
    public final WebError translate(Throwable throwable) {
        return doTranslate(throwable);
    }

    /**
     * 策略翻译，子类可覆盖以先归一框架特定异常（如 JAX-RS {@code WebApplicationException}），
     * 再委托 {@code super.doTranslate} 走通用翻译。
     *
     * @param throwable 原始异常
     * @return 转换后的 Web 错误
     */
    protected WebError doTranslate(Throwable throwable) {
        return translator.translate(throwable);
    }

    /**
     * 统一 {@link R} 响应体。
     *
     * @param error Web 错误
     * @return 统一错误响应
     */
    public final R<Object> toResponse(WebError error) {
        return responseBuilder.toResponse(error);
    }

    /**
     * status &gt;= 500 视为未处理的服务端错误。
     *
     * @param error Web 错误
     * @return 服务器错误时为 true
     */
    protected final boolean isServerError(WebError error) {
        return responseBuilder.isServerError(error);
    }

    /**
     * JSON 序列化失败时的兜底响应体。
     *
     * @return 默认错误响应体
     */
    protected final byte[] fallbackBody() {
        return responseBuilder.fallbackBody();
    }

    /**
     * 统一的未处理错误日志：status &gt;= 500 时按 error 级别记录，子类在写响应前调用。
     *
     * @param throwable 原始异常
     * @param error Web 错误
     */
    protected void logUnhandled(Throwable throwable, WebError error) {
        if (isServerError(error)) {
            LOG.error("Unhandled {} request failure", frameworkName(), throwable);
        }
    }

    /**
     * 框架名，用于未处理错误日志定位。
     *
     * @return 框架名称
     */
    protected abstract String frameworkName();
}
