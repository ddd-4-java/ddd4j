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
package io.ddd4j.web.webmvc;

import io.ddd4j.core.api.R;
import io.ddd4j.web.core.error.WebError;
import io.ddd4j.web.core.error.WebExceptionTranslator;
import io.ddd4j.web.error.BaseErrorConfiguration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Objects;

/**
 * 将 WebMVC 异常统一翻译为真实 HTTP 状态和 {@link R} 响应体。
 *
 * <p>翻译、5xx 日志与响应体构造复用 {@link BaseErrorConfiguration}，此处只保留
 * Spring MVC 的 {@code @ExceptionHandler} 响应写出。
 */
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice
public class Ddd4jWebMvcExceptionHandler extends BaseErrorConfiguration {

    /**
     * \u521b\u5efa Ddd4jWebMvcExceptionHandler \u5b9e\u4f8b\u3002
     * @param exceptionTranslator \u5f02\u5e38\u8f6c\u6362\u5668
     */
    public Ddd4jWebMvcExceptionHandler(WebExceptionTranslator exceptionTranslator) {
        super(Objects.requireNonNull(exceptionTranslator, "exceptionTranslator must not be null"));
    }

    /**
     * 返回当前适配实现对应的运行框架名称。
     *
     * @return 框架标识
     */
    @Override
    protected String frameworkName() {
        return "WebMVC";
    }

    /**
     * \u5c06\u5f02\u5e38\u8f6c\u6362\u4e3a HTTP \u54cd\u5e94\u3002
     * @param throwable \u6355\u83b7\u7684\u5f02\u5e38
     * @return HTTP \u9519\u8bef\u54cd\u5e94
     */
    @ExceptionHandler(Throwable.class)
    public ResponseEntity<R<Object>> handle(Throwable throwable) {
        WebError error = translate(throwable);
        logUnhandled(throwable, error);
        return ResponseEntity.status(error.status()).body(toResponse(error));
    }
}
