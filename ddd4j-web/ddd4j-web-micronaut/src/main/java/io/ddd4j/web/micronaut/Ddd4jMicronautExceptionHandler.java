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
package io.ddd4j.web.micronaut;

import io.ddd4j.web.core.error.WebError;
import io.ddd4j.web.core.error.WebExceptionTranslator;
import io.ddd4j.web.error.BaseErrorConfiguration;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.server.exceptions.ExceptionHandler;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

import java.util.Objects;

/**
 * Micronaut 全局异常到 ddd4j R 响应的映射。
 *
 * <p>翻译与响应体构造复用 {@link BaseErrorConfiguration}，此处只保留
 * Micronaut 的 {@code HttpResponse} 写出与请求上下文日志。
 */
@Slf4j
@Singleton
public final class Ddd4jMicronautExceptionHandler extends BaseErrorConfiguration implements ExceptionHandler<Throwable, HttpResponse<?>> {

    // 为 Netty 默认 4096 字节状态行预留 HTTP 版本、状态码和分隔符。
    /**
     * HTTP 状态原因短语允许的长度上限。
     */
    private static final int MAX_REASON_LENGTH = 4080;

    /**
     * 使用默认异常翻译器创建处理器。
     */
    @Inject
    public Ddd4jMicronautExceptionHandler() {
        super();
    }

    /**
     * 使用指定异常翻译器创建处理器。
     *
     * @param translator 异常翻译器
     */
    public Ddd4jMicronautExceptionHandler(WebExceptionTranslator translator) {
        super(translator);
    }

    /**
     * 返回当前适配实现对应的运行框架名称。
     *
     * @return 框架标识
     */
    @Override
    protected String frameworkName() {
        return "Micronaut";
    }

    /**
     * 将 Micronaut 请求异常转换为带状态和正文的响应。
     *
     * @param request   当前进入调用链的 HTTP 请求
     * @param exception 当前请求处理时抛出的异常
     * @return 统一的 Micronaut HTTP 错误响应
     */
    @Override
    public HttpResponse<?> handle(HttpRequest request, Throwable exception) {
        WebError error = translate(exception);
        if (isServerError(error)) {
            log.error("Unhandled {} request failure: {} {}", frameworkName(), request.getMethodName(),
                    request.getPath(), exception);
        }
        String reason = error.message();
        if (Objects.nonNull(reason)
                && (reason.length() > MAX_REASON_LENGTH || reason.indexOf('\r') >= 0 || reason.indexOf('\n') >= 0)) {
            reason = null;
        }
        return HttpResponse.status(error.status(), reason).body(toResponse(error));
    }
}
