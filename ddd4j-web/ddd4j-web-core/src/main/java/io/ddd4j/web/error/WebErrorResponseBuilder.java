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
import io.ddd4j.web.core.error.WebError;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * 统一错误响应构造器。
 *
 * <p>收敛各 Web 框架适配器（WebMVC/WebFlux/Quarkus/Helidon/Dropwizard/Micronaut）
 * 重复的响应语义：{@link WebError} 到 {@link R} 响应体的转换、服务端错误的判定，
 * 以及 JSON 序列化失败时的兜底响应体。
 */
public final class WebErrorResponseBuilder {

    /**
     * JSON 序列化失败时的兜底响应体（500，Internal Server Error）。
     */
    public static final String INTERNAL_ERROR_FALLBACK_JSON =
            "{\"code\":500,\"msg\":\"Internal Server Error\",\"data\":null}";

    private static final WebErrorResponseBuilder INSTANCE = new WebErrorResponseBuilder();

    private WebErrorResponseBuilder() {
    }

    /**
     * @return 共享的默认构造器实例（无状态，可安全复用）。
     */
    public static WebErrorResponseBuilder defaults() {
        return INSTANCE;
    }

    /**
     * 将统一错误表示转换为 {@link R} 响应体。
     */
    public R<Object> toResponse(WebError error) {
        return Objects.requireNonNull(error, "error must not be null").toResponse();
    }

    /**
     * status &gt;= 500 视为未处理的服务端错误，应按 error 级别记录日志。
     */
    public boolean isServerError(WebError error) {
        return Objects.requireNonNull(error, "error must not be null").status() >= 500;
    }

    /**
     * @return 兜底响应体的 UTF-8 字节，供流式响应直接写出。
     */
    public byte[] fallbackBody() {
        return INTERNAL_ERROR_FALLBACK_JSON.getBytes(StandardCharsets.UTF_8);
    }
}
