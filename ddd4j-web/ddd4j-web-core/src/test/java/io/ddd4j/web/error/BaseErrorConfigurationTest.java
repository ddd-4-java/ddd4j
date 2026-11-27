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
import io.ddd4j.web.core.error.DefaultWebExceptionTranslator;
import io.ddd4j.web.core.error.WebError;
import io.ddd4j.web.core.error.WebExceptionTranslator;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 验证 <code>BaseErrorConfiguration</code> 在<code>toResponse</code>、<code>httpStatusErrorFallsBack</code>等操作中的行为契约。
 */
class BaseErrorConfigurationTest {

    /**
     * 验证<code>error.status()</code> 等于 <code>400</code>，<code>error.message()</code> 等于 <code>"bad"</code>。
     */
    @Test
    void defaultConstructorUsesDefaultTranslator() {
        BaseErrorConfiguration configuration = new PassThroughConfiguration();

        WebError error = configuration.translate(new IllegalArgumentException("bad"));

        assertEquals(400, error.status());
        assertEquals("bad", error.message());
    }

    /**
     * 验证被测调用 抛出 <code>NullPointerException.class</code>。
     */
    @Test
    void rejectsNullTranslatorAndBuilder() {
        assertThrows(NullPointerException.class,
                () -> new StubErrorConfiguration(null, null, WebErrorResponseBuilder.defaults()));
        assertThrows(NullPointerException.class,
                () -> new StubErrorConfiguration(null, new DefaultWebExceptionTranslator(), null));
    }

    /**
     * 验证<code>configuration.translate(new RuntimeException("ignored"))</code> 等于 <code>stubbed</code>。
     */
    @Test
    void doTranslateOverrideShortCircuitsTranslator() {
        WebError stubbed = new WebError(418, "TEAPOT", "short circuited", null);
        BaseErrorConfiguration configuration = new StubErrorConfiguration(stubbed);

        assertEquals(stubbed, configuration.translate(new RuntimeException("ignored")));
    }

    /**
     * 验证<code>response.getCode()</code> 等于 <code>"VALIDATION"</code>，<code>response.getMsg()</code> 等于 <code>"bad input"</code>，<code>response.getData()</code> 等于 <code>"detail"</code>。
     */
    @Test
    void toResponseDelegatesToWebError() {
        WebError error = new WebError(422, "VALIDATION", "bad input", "detail");
        BaseErrorConfiguration configuration = new StubErrorConfiguration(error);

        R<Object> response = configuration.toResponse(error);

        assertEquals("VALIDATION", response.getCode());
        assertEquals("bad input", response.getMsg());
        assertEquals("detail", response.getData());
    }

    /**
     * 验证<code>configuration.toResponse(null)</code> 抛出 <code>NullPointerException.class</code>。
     */
    @Test
    void toResponseRejectsNullError() {
        BaseErrorConfiguration configuration = new StubErrorConfiguration(null);

        assertThrows(NullPointerException.class, () -> configuration.toResponse(null));
    }

    /**
     * 验证<code>configuration.isServerError(new WebError(499, 499, "client", null))</code>为假，<code>configuration.isServerError(new WebError(500, 500, "server", null))</code>为真。
     */
    @Test
    void isServerErrorUsesThreshold500() {
        BaseErrorConfiguration configuration = new StubErrorConfiguration(null);

        assertFalse(configuration.isServerError(new WebError(499, 499, "client", null)));
        assertTrue(configuration.isServerError(new WebError(500, 500, "server", null)));
    }

    /**
     * 验证<code>configuration</code>非空。
     */
    @Test
    void logUnhandledToleratesClientAndServerErrors() {
        BaseErrorConfiguration configuration = new StubErrorConfiguration(null);

        configuration.logUnhandled(new Exception("client"), new WebError(400, 400, "client", null));
        configuration.logUnhandled(new Exception("server"), new WebError(500, 500, "server", null));

        assertNotNull(configuration);
    }

    /**
     * 验证<code>error.status()</code> 等于 <code>404</code>，<code>error.code()</code> 等于 <code>404</code>，<code>error.message()</code> 等于 <code>"Not Found"</code>。
     */
    @Test
    void httpStatusErrorFallsBackWhenMessageBlank() {
        WebError error = BaseErrorConfiguration.httpStatusError(404, "Not Found", "  ");

        assertEquals(404, error.status());
        assertEquals(404, error.code());
        assertEquals("Not Found", error.message());
    }

    /**
     * 验证<code>error.message()</code> 等于 <code>"duplicate order"</code>。
     */
    @Test
    void httpStatusErrorKeepsGivenMessage() {
        WebError error = BaseErrorConfiguration.httpStatusError(409, "Conflict", "duplicate order");

        assertEquals("duplicate order", error.message());
    }

    /**
     * 验证<code>builder.fallbackBody()</code> 与…的元素逐项相同 <code>WebErrorResponseBuilder.INTERNAL_ERROR_FALLBACK_JSON.getBytes(StandardCharsets.UTF_8)</code>，<code>builder.toResponse(new WebError(500, 500, "boom", null)).getCode()</code> 等于 <code>500</code>，<code>builder.isServerError(new WebError(404, 404, "missing", null))</code>为假。
     */
    @Test
    void builderBuildsFallbackBodyAndResponses() {
        WebErrorResponseBuilder builder = WebErrorResponseBuilder.defaults();

        assertArrayEquals(WebErrorResponseBuilder.INTERNAL_ERROR_FALLBACK_JSON.getBytes(StandardCharsets.UTF_8),
                builder.fallbackBody());
        assertEquals(500, builder.toResponse(new WebError(500, 500, "boom", null)).getCode());
        assertTrue(builder.isServerError(new WebError(503, 503, "unavailable", null)));
        assertFalse(builder.isServerError(new WebError(404, 404, "missing", null)));
    }

    /**
     * 固定翻译结果的桩基类，便于断言模板方法管线。
     */
    private static final class StubErrorConfiguration extends BaseErrorConfiguration {

        /**
         * 记录测试替身已准备的响应或其状态。
         */
        private final WebError stubbed;

        /**
         * 使用 <code>super()</code> 初始化 <code>StubErrorConfiguration</code> 测试夹具。
         *
         * @param stubbed         用于初始化 <code>StubErrorConfiguration</code> 的 <code>WebError</code> 值
         * @param translator      用于初始化 <code>StubErrorConfiguration</code> 的 <code>WebExceptionTranslator</code> 值
         * @param responseBuilder 用于初始化 <code>StubErrorConfiguration</code> 的 <code>WebErrorResponseBuilder</code> 值
         */
        StubErrorConfiguration(WebError stubbed, WebExceptionTranslator translator,
                               WebErrorResponseBuilder responseBuilder) {
            super(translator, responseBuilder);
            this.stubbed = stubbed;
        }

        /**
         * 使用 <code>this()</code> 初始化 <code>StubErrorConfiguration</code> 测试夹具。
         *
         * @param stubbed 用于初始化 <code>StubErrorConfiguration</code> 的 <code>WebError</code> 值
         */
        StubErrorConfiguration(WebError stubbed) {
            this(stubbed, new DefaultWebExceptionTranslator(), WebErrorResponseBuilder.defaults());
        }

        /**
         * 返回当前适配实现对应的运行框架名称。
         *
         * @return 实际返回<code>"Stub"</code>，供调用方取得当前值
         */
        @Override
        protected String frameworkName() {
            return "Stub";
        }

        /**
         * 策略翻译，子类可覆盖以先归一框架特定异常（如 JAX-RS {@code WebApplicationException}），。
         *
         * @param throwable 原始异常
         * @return 实际返回<code>stubbed</code>，供调用方取得当前值
         */
        @Override
        protected WebError doTranslate(Throwable throwable) {
            return stubbed;
        }
    }

    /**
     * 不覆盖 {@code doTranslate} 的桩基类，验证默认翻译策略接线。
     */
    private static final class PassThroughConfiguration extends BaseErrorConfiguration {

        /**
         * 使用 <code>super()</code> 初始化 <code>PassThroughConfiguration</code> 测试夹具。
         */
        PassThroughConfiguration() {
            super();
        }

        /**
         * 返回当前适配实现对应的运行框架名称。
         *
         * @return 实际返回<code>"Stub"</code>，供调用方取得当前值
         */
        @Override
        protected String frameworkName() {
            return "Stub";
        }
    }
}
