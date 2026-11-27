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
import io.ddd4j.web.core.error.WebExceptionTranslator;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

/** \u9a8c\u8bc1 <code>Ddd4jMicronautExceptionHandler</code> \u5728<code>constructor</code>\u7b49\u64cd\u4f5c\u4e2d\u7684\u884c\u4e3a\u5951\u7ea6\u3002 */
class Ddd4jMicronautExceptionHandlerTest {

    /** \u9a8c\u8bc1<code>new Ddd4jMicronautExceptionHandler((WebExceptionTranslator) null)</code> \u629b\u51fa <code>NullPointerException</code>\u3002 */
    @Test
    void constructorRejectsNullTranslator() {
        assertThatNullPointerException()
                .isThrownBy(() -> new Ddd4jMicronautExceptionHandler((WebExceptionTranslator) null));
    }

    /** \u9a8c\u8bc1<code>handler</code>\u975e\u7a7a\u3002 */
    @Test
    void defaultConstructorCreatesDefaultTranslator() {
        Ddd4jMicronautExceptionHandler handler = new Ddd4jMicronautExceptionHandler();
        assertThat(handler).isNotNull();
    }

    /** \u9a8c\u8bc1<code>response.getStatus().getCode()</code> \u7b49\u4e8e <code>400</code>\uff0c<code>response.body()</code>\u975e\u7a7a\u3002 */
    @Test
    void handleTranslatesClientErrorToResponse() {
        Ddd4jMicronautExceptionHandler handler =
                new Ddd4jMicronautExceptionHandler(new DefaultWebExceptionTranslator());
        HttpRequest<?> request = HttpRequest.GET("/test");
        IllegalArgumentException ex = new IllegalArgumentException("bad arg");

        HttpResponse<?> response = handler.handle(request, ex);

        assertThat(response.getStatus().getCode()).isEqualTo(400);
        assertThat(response.body()).isNotNull();
    }

    /** \u9a8c\u8bc1<code>response.getStatus().getCode()</code> \u5927\u4e8e\u6216\u7b49\u4e8e <code>500</code>\u3002 */
    @Test
    void handleTranslatesServerErrorToResponse() {
        Ddd4jMicronautExceptionHandler handler =
                new Ddd4jMicronautExceptionHandler(new DefaultWebExceptionTranslator());
        HttpRequest<?> request = HttpRequest.GET("/crash");
        RuntimeException ex = new RuntimeException("internal failure");

        HttpResponse<?> response = handler.handle(request, ex);

        assertThat(response.getStatus().getCode()).isGreaterThanOrEqualTo(500);
    }

    /** \u9a8c\u8bc1<code>response.getStatus().getCode()</code> \u7b49\u4e8e <code>409</code>\u3002 */
    @Test
    void handleTranslatesConflictForIllegalState() {
        Ddd4jMicronautExceptionHandler handler =
                new Ddd4jMicronautExceptionHandler(new DefaultWebExceptionTranslator());
        HttpRequest<?> request = HttpRequest.GET("/conflict");
        IllegalStateException ex = new IllegalStateException("not allowed");

        HttpResponse<?> response = handler.handle(request, ex);

        assertThat(response.getStatus().getCode()).isEqualTo(409);
    }

    /** \u9a8c\u8bc1<code>response.getStatus().getCode()</code> \u7b49\u4e8e <code>403</code>\u3002 */
    @Test
    void handleTranslatesUnauthorized() {
        Ddd4jMicronautExceptionHandler handler =
                new Ddd4jMicronautExceptionHandler(new DefaultWebExceptionTranslator());
        HttpRequest<?> request = HttpRequest.GET("/secure");
        SecurityException ex = new SecurityException("denied");

        HttpResponse<?> response = handler.handle(request, ex);

        assertThat(response.getStatus().getCode()).isEqualTo(403);
    }
}