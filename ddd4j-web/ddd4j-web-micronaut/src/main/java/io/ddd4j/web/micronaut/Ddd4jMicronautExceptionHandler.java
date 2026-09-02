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

/**
 * Micronaut 全局异常到 ddd4j R 响应的映射。
 *
 * <p>翻译与响应体构造复用 {@link BaseErrorConfiguration}，此处只保留
 * Micronaut 的 {@code HttpResponse} 写出与请求上下文日志。
 */
@Slf4j
@Singleton
public final class Ddd4jMicronautExceptionHandler extends BaseErrorConfiguration implements ExceptionHandler<Throwable, HttpResponse<?>> {

    @Inject
    public Ddd4jMicronautExceptionHandler() {
        super();
    }

    public Ddd4jMicronautExceptionHandler(WebExceptionTranslator translator) {
        super(translator);
    }

    @Override
    protected String frameworkName() {
        return "Micronaut";
    }

    @Override
    public HttpResponse<?> handle(HttpRequest request, Throwable exception) {
        WebError error = translate(exception);
        if (isServerError(error)) {
            log.error("Unhandled {} request failure: {} {}", frameworkName(), request.getMethodName(),
                    request.getPath(), exception);
        }
        return HttpResponse.status(error.status(), error.message()).body(toResponse(error));
    }
}
