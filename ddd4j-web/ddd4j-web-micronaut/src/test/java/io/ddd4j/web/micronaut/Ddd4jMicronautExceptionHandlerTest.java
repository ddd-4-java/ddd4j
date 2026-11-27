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

import io.ddd4j.core.api.R;
import io.ddd4j.web.core.error.DefaultWebExceptionTranslator;
import io.ddd4j.web.core.error.WebExceptionTranslator;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

/**
 * 验证 <code>Ddd4jMicronautExceptionHandler</code> 在<code>constructor</code>等操作中的行为契约。
 */
class Ddd4jMicronautExceptionHandlerTest {

    /**
     * 验证<code>new Ddd4jMicronautExceptionHandler((WebExceptionTranslator) null)</code> 抛出 <code>NullPointerException</code>。
     */
    @Test
    void constructorRejectsNullTranslator() {
        assertThatNullPointerException()
                .isThrownBy(() -> new Ddd4jMicronautExceptionHandler((WebExceptionTranslator) null));
    }

    /**
     * 验证<code>handler</code>非空。
     */
    @Test
    void defaultConstructorCreatesDefaultTranslator() {
        Ddd4jMicronautExceptionHandler handler = new Ddd4jMicronautExceptionHandler();
        assertThat(handler).isNotNull();
    }

    /**
     * 验证<code>response.getStatus().getCode()</code> 等于 <code>400</code>，<code>response.body()</code>非空。
     */
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

    /**
     * 验证<code>response.getStatus().getCode()</code> 大于或等于 <code>500</code>。
     */
    @Test
    void handleTranslatesServerErrorToResponse() {
        Ddd4jMicronautExceptionHandler handler =
                new Ddd4jMicronautExceptionHandler(new DefaultWebExceptionTranslator());
        HttpRequest<?> request = HttpRequest.GET("/crash");
        RuntimeException ex = new RuntimeException("internal failure");

        HttpResponse<?> response = handler.handle(request, ex);

        assertThat(response.getStatus().getCode()).isGreaterThanOrEqualTo(500);
    }

    /**
     * 验证<code>response.getStatus().getCode()</code> 等于 <code>409</code>。
     */
    @Test
    void handleTranslatesConflictForIllegalState() {
        Ddd4jMicronautExceptionHandler handler =
                new Ddd4jMicronautExceptionHandler(new DefaultWebExceptionTranslator());
        HttpRequest<?> request = HttpRequest.GET("/conflict");
        IllegalStateException ex = new IllegalStateException("not allowed");

        HttpResponse<?> response = handler.handle(request, ex);

        assertThat(response.getStatus().getCode()).isEqualTo(409);
    }

    /**
     * 验证<code>response.getStatus().getCode()</code> 等于 <code>403</code>。
     */
    @Test
    void handleTranslatesUnauthorized() {
        Ddd4jMicronautExceptionHandler handler =
                new Ddd4jMicronautExceptionHandler(new DefaultWebExceptionTranslator());
        HttpRequest<?> request = HttpRequest.GET("/secure");
        SecurityException ex = new SecurityException("denied");

        HttpResponse<?> response = handler.handle(request, ex);

        assertThat(response.getStatus().getCode()).isEqualTo(403);
    }

    /**
     * 验证<code>response.code()</code> 等于 <code>409</code>，<code>response.reason()</code> 等于 <code>"inventory is locked"</code>，<code>((R&lt;?&gt;) response.body()).getMsg()</code> 等于 <code>"inventory is locked"</code>。
     */
    @Test
    void retainsSingleLineReasonAndErrorEnvelope() {
        HttpResponse<?> response = new Ddd4jMicronautExceptionHandler().handle(
                HttpRequest.GET("/conflict"), new IllegalStateException("inventory is locked"));

        assertThat(response.code()).isEqualTo(409);
        assertThat(response.reason()).isEqualTo("inventory is locked");
        assertThat(((R<?>) response.body()).getMsg()).isEqualTo("inventory is locked");
    }

    /**
     * 验证<code>response.code()</code> 等于 <code>409</code>，<code>response.reason()</code> 等于 <code>"Conflict"</code>，<code>((R&lt;?&gt;) response.body()).getMsg()</code> 等于 <code>message</code>。
     */
    @Test
    void keepsMultilineErrorInBodyAndUsesStandardReason() {
        String message = "inventory is locked\r\ntry again later";
        HttpResponse<?> response = new Ddd4jMicronautExceptionHandler().handle(
                HttpRequest.GET("/conflict"), new IllegalStateException(message));

        assertThat(response.code()).isEqualTo(409);
        assertThat(response.reason()).isEqualTo("Conflict");
        assertThat(((R<?>) response.body()).getMsg()).isEqualTo(message);
    }

    /**
     * 验证<code>response.code()</code> 等于 <code>409</code>，<code>response.reason()</code> 的大小为 <code>"Conflict".length()</code>，<code>((R&lt;?&gt;) response.body()).getMsg()</code> 等于 <code>message</code>。
     */
    @Test
    void longErrorKeepsBodyAndUsesStandardReason() {
        String message = String.join("", Collections.nCopies(4081, "x"));
        HttpResponse<?> response = new Ddd4jMicronautExceptionHandler().handle(
                HttpRequest.GET("/conflict"), new IllegalStateException(message));

        assertThat(response.code()).isEqualTo(409);
        assertThat(response.reason()).hasSize("Conflict".length()).isEqualTo("Conflict");
        assertThat(((R<?>) response.body()).getMsg()).isEqualTo(message);
    }
}
