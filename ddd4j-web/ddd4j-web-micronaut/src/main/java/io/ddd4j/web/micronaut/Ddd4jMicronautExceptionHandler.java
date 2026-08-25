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

import io.ddd4j.web.core.error.DefaultWebExceptionTranslator;
import io.ddd4j.web.core.error.WebError;
import io.ddd4j.web.core.error.WebExceptionTranslator;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.server.exceptions.ExceptionHandler;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

import java.util.Objects;

/**
 * Micronaut 全局异常到 ddd4j R 响应的映射。
 */
@Slf4j
@Singleton
public final class Ddd4jMicronautExceptionHandler implements ExceptionHandler<Throwable, HttpResponse<?>> {

    private final WebExceptionTranslator translator;

    @Inject
    public Ddd4jMicronautExceptionHandler() {
        this(new DefaultWebExceptionTranslator());
    }

    public Ddd4jMicronautExceptionHandler(WebExceptionTranslator translator) {
        this.translator = Objects.requireNonNull(translator, "translator must not be null");
    }

    @Override
    public HttpResponse<?> handle(HttpRequest request, Throwable exception) {
        WebError error = translator.translate(exception);
        if (error.status() >= 500) {
            log.error("Unhandled HTTP request failure: {} {}", request.getMethodName(), request.getPath(), exception);
        }
        return HttpResponse.status(error.status(), error.message()).body(error.toResponse());
    }
}
