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
    /** HTTP \u72b6\u6001\u539f\u56e0\u77ed\u8bed\u5141\u8bb8\u7684\u957f\u5ea6\u4e0a\u9650\u3002 */
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

    /** {@inheritDoc} */
    @Override
    protected String frameworkName() {
        return "Micronaut";
    }

    /**
     * \u5c06 Micronaut \u8bf7\u6c42\u5f02\u5e38\u8f6c\u6362\u4e3a\u5e26\u72b6\u6001\u548c\u6b63\u6587\u7684\u54cd\u5e94\u3002
     * @param request \u5f53\u524d\u8fdb\u5165\u8c03\u7528\u94fe\u7684 HTTP \u8bf7\u6c42
     * @param exception \u5f53\u524d\u8bf7\u6c42\u5904\u7406\u65f6\u629b\u51fa\u7684\u5f02\u5e38
     * @return \u7edf\u4e00\u7684 Micronaut HTTP \u9519\u8bef\u54cd\u5e94
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
