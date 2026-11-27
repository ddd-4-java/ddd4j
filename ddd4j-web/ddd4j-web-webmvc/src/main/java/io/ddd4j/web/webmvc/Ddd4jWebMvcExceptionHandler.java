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

import io.ddd4j.core.api.ApiCode;
import io.ddd4j.core.api.R;
import io.ddd4j.core.exception.AuthenticationException;
import io.ddd4j.core.exception.AuthorizationException;
import io.ddd4j.web.core.error.WebError;
import io.ddd4j.web.core.error.WebExceptionTranslator;
import io.ddd4j.web.error.BaseErrorConfiguration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Objects;

/**
 * 将 WebMVC 异常统一翻译为真实 HTTP 状态和 {@link R} 响应体。
 *
 * <p>Spring MVC 请求通过注入的翻译器构造响应。认证响应方法也可由调用方直接使用，
 * 此时按框架默认状态与消息构造响应。
 */
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice
public class Ddd4jWebMvcExceptionHandler extends BaseErrorConfiguration {

    /**
     * 创建 Ddd4jWebMvcExceptionHandler 实例。
     *
     * @param exceptionTranslator 异常转换器
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
     * 为直接调用方构造默认认证失败响应。
     *
     * @param exception 认证失败异常
     * @return 使用异常消息或默认说明的 401 响应
     */
    public ResponseEntity<R<String>> authenticationFailed(AuthenticationException exception) {
        String message = exception.getMessage();
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(
                ApiCode.UNAUTHORIZED.toResponse(StringUtils.hasText(message) ? message : ApiCode.UNAUTHORIZED.getReason()));
    }

    /**
     * 为直接调用方构造默认授权失败响应。
     *
     * @param exception 授权失败异常
     * @return 使用异常消息或默认说明的 403 响应
     */
    public ResponseEntity<R<String>> authorizationFailed(AuthorizationException exception) {
        String message = exception.getMessage();
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(
                ApiCode.FORBIDDEN.toResponse(StringUtils.hasText(message) ? message : ApiCode.FORBIDDEN.getReason()));
    }

    /**
     * 将异常转换为 HTTP 响应。
     *
     * @param throwable 捕获的异常
     * @return HTTP 错误响应
     */
    @ExceptionHandler(Throwable.class)
    public ResponseEntity<R<Object>> handle(Throwable throwable) {
        WebError error = translate(throwable);
        logUnhandled(throwable, error);
        return ResponseEntity.status(error.status()).body(toResponse(error));
    }
}
