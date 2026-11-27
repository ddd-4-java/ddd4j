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

    /** \u4f9b\u5404\u6846\u67b6\u5171\u7528\u7684\u9519\u8bef\u54cd\u5e94\u6784\u9020\u5668\u5b9e\u4f8b\u3002 */
    private static final WebErrorResponseBuilder INSTANCE = new WebErrorResponseBuilder();

    /** \u9650\u5236\u8c03\u7528\u65b9\u76f4\u63a5\u521b\u5efa <code>WebErrorResponseBuilder</code> \u5b9e\u4f8b\u3002 */
    private WebErrorResponseBuilder() {
    }

    /** @return \u5171\u4eab\u7684\u9ed8\u8ba4\u6784\u9020\u5668\u5b9e\u4f8b\uff08\u65e0\u72b6\u6001\uff0c\u53ef\u5b89\u5168\u590d\u7528\uff09\u3002 */
    public static WebErrorResponseBuilder defaults() {
        return INSTANCE;
    }

    /**
     * \u5c06\u7edf\u4e00\u9519\u8bef\u8868\u793a\u8f6c\u6362\u4e3a {@link R} \u54cd\u5e94\u4f53\u3002
     *
     * @param error Web \u9519\u8bef
     * @return \u7edf\u4e00\u9519\u8bef\u54cd\u5e94
     */
    public R<Object> toResponse(WebError error) {
        return Objects.requireNonNull(error, "error must not be null").toResponse();
    }

    /**
     * status &gt;= 500 \u89c6\u4e3a\u672a\u5904\u7406\u7684\u670d\u52a1\u7aef\u9519\u8bef\uff0c\u5e94\u6309 error \u7ea7\u522b\u8bb0\u5f55\u65e5\u5fd7\u3002
     *
     * @param error Web \u9519\u8bef
     * @return \u670d\u52a1\u5668\u9519\u8bef\u65f6\u4e3a true
     */
    public boolean isServerError(WebError error) {
        return Objects.requireNonNull(error, "error must not be null").status() >= 500;
    }

    /** @return \u515c\u5e95\u54cd\u5e94\u4f53\u7684 UTF-8 \u5b57\u8282\uff0c\u4f9b\u6d41\u5f0f\u54cd\u5e94\u76f4\u63a5\u5199\u51fa\u3002 */
    public byte[] fallbackBody() {
        return INTERNAL_ERROR_FALLBACK_JSON.getBytes(StandardCharsets.UTF_8);
    }
}
