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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;

import io.ddd4j.core.api.R;
import io.ddd4j.web.core.error.DefaultWebExceptionTranslator;
import io.ddd4j.web.core.error.WebError;
import io.ddd4j.web.core.error.WebExceptionTranslator;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

/** \u9a8c\u8bc1 <code>BaseErrorConfiguration</code> \u5728<code>toResponse</code>\u3001<code>httpStatusErrorFallsBack</code>\u7b49\u64cd\u4f5c\u4e2d\u7684\u884c\u4e3a\u5951\u7ea6\u3002 */
class BaseErrorConfigurationTest {

    /** \u9a8c\u8bc1<code>error.status()</code> \u7b49\u4e8e <code>400</code>\uff0c<code>error.message()</code> \u7b49\u4e8e <code>"bad"</code>\u3002 */
    @Test
    void defaultConstructorUsesDefaultTranslator() {
        BaseErrorConfiguration configuration = new PassThroughConfiguration();

        WebError error = configuration.translate(new IllegalArgumentException("bad"));

        assertEquals(400, error.status());
        assertEquals("bad", error.message());
    }

    /** \u9a8c\u8bc1\u88ab\u6d4b\u8c03\u7528 \u629b\u51fa <code>NullPointerException.class</code>\u3002 */
    @Test
    void rejectsNullTranslatorAndBuilder() {
        assertThrows(NullPointerException.class,
                () -> new StubErrorConfiguration(null, null, WebErrorResponseBuilder.defaults()));
        assertThrows(NullPointerException.class,
                () -> new StubErrorConfiguration(null, new DefaultWebExceptionTranslator(), null));
    }

    /** \u9a8c\u8bc1<code>configuration.translate(new RuntimeException("ignored"))</code> \u7b49\u4e8e <code>stubbed</code>\u3002 */
    @Test
    void doTranslateOverrideShortCircuitsTranslator() {
        WebError stubbed = new WebError(418, "TEAPOT", "short circuited", null);
        BaseErrorConfiguration configuration = new StubErrorConfiguration(stubbed);

        assertEquals(stubbed, configuration.translate(new RuntimeException("ignored")));
    }

    /** \u9a8c\u8bc1<code>response.getCode()</code> \u7b49\u4e8e <code>"VALIDATION"</code>\uff0c<code>response.getMsg()</code> \u7b49\u4e8e <code>"bad input"</code>\uff0c<code>response.getData()</code> \u7b49\u4e8e <code>"detail"</code>\u3002 */
    @Test
    void toResponseDelegatesToWebError() {
        WebError error = new WebError(422, "VALIDATION", "bad input", "detail");
        BaseErrorConfiguration configuration = new StubErrorConfiguration(error);

        R<Object> response = configuration.toResponse(error);

        assertEquals("VALIDATION", response.getCode());
        assertEquals("bad input", response.getMsg());
        assertEquals("detail", response.getData());
    }

    /** \u9a8c\u8bc1<code>configuration.toResponse(null)</code> \u629b\u51fa <code>NullPointerException.class</code>\u3002 */
    @Test
    void toResponseRejectsNullError() {
        BaseErrorConfiguration configuration = new StubErrorConfiguration(null);

        assertThrows(NullPointerException.class, () -> configuration.toResponse(null));
    }

    /** \u9a8c\u8bc1<code>configuration.isServerError(new WebError(499, 499, "client", null))</code>\u4e3a\u5047\uff0c<code>configuration.isServerError(new WebError(500, 500, "server", null))</code>\u4e3a\u771f\u3002 */
    @Test
    void isServerErrorUsesThreshold500() {
        BaseErrorConfiguration configuration = new StubErrorConfiguration(null);

        assertFalse(configuration.isServerError(new WebError(499, 499, "client", null)));
        assertTrue(configuration.isServerError(new WebError(500, 500, "server", null)));
    }

    /** \u9a8c\u8bc1<code>configuration</code>\u975e\u7a7a\u3002 */
    @Test
    void logUnhandledToleratesClientAndServerErrors() {
        BaseErrorConfiguration configuration = new StubErrorConfiguration(null);

        configuration.logUnhandled(new Exception("client"), new WebError(400, 400, "client", null));
        configuration.logUnhandled(new Exception("server"), new WebError(500, 500, "server", null));

        assertNotNull(configuration);
    }

    /** \u9a8c\u8bc1<code>error.status()</code> \u7b49\u4e8e <code>404</code>\uff0c<code>error.code()</code> \u7b49\u4e8e <code>404</code>\uff0c<code>error.message()</code> \u7b49\u4e8e <code>"Not Found"</code>\u3002 */
    @Test
    void httpStatusErrorFallsBackWhenMessageBlank() {
        WebError error = BaseErrorConfiguration.httpStatusError(404, "Not Found", "  ");

        assertEquals(404, error.status());
        assertEquals(404, error.code());
        assertEquals("Not Found", error.message());
    }

    /** \u9a8c\u8bc1<code>error.message()</code> \u7b49\u4e8e <code>"duplicate order"</code>\u3002 */
    @Test
    void httpStatusErrorKeepsGivenMessage() {
        WebError error = BaseErrorConfiguration.httpStatusError(409, "Conflict", "duplicate order");

        assertEquals("duplicate order", error.message());
    }

    /** \u9a8c\u8bc1<code>builder.fallbackBody()</code> \u4e0e\u2026\u7684\u5143\u7d20\u9010\u9879\u76f8\u540c <code>WebErrorResponseBuilder.INTERNAL_ERROR_FALLBACK_JSON.getBytes(StandardCharsets.UTF_8)</code>\uff0c<code>builder.toResponse(new WebError(500, 500, "boom", null)).getCode()</code> \u7b49\u4e8e <code>500</code>\uff0c<code>builder.isServerError(new WebError(404, 404, "missing", null))</code>\u4e3a\u5047\u3002 */
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

        /** \u8bb0\u5f55\u6d4b\u8bd5\u66ff\u8eab\u5df2\u51c6\u5907\u7684\u54cd\u5e94\u6216\u5176\u72b6\u6001\u3002 */
        private final WebError stubbed;

        /**
         * \u4f7f\u7528 <code>super()</code> \u521d\u59cb\u5316 <code>StubErrorConfiguration</code> \u6d4b\u8bd5\u5939\u5177\u3002
         * @param stubbed \u7528\u4e8e\u521d\u59cb\u5316 <code>StubErrorConfiguration</code> \u7684 <code>WebError</code> \u503c
         * @param translator \u7528\u4e8e\u521d\u59cb\u5316 <code>StubErrorConfiguration</code> \u7684 <code>WebExceptionTranslator</code> \u503c
         * @param responseBuilder \u7528\u4e8e\u521d\u59cb\u5316 <code>StubErrorConfiguration</code> \u7684 <code>WebErrorResponseBuilder</code> \u503c
         */
        StubErrorConfiguration(WebError stubbed, WebExceptionTranslator translator,
                               WebErrorResponseBuilder responseBuilder) {
            super(translator, responseBuilder);
            this.stubbed = stubbed;
        }

        /**
         * \u4f7f\u7528 <code>this()</code> \u521d\u59cb\u5316 <code>StubErrorConfiguration</code> \u6d4b\u8bd5\u5939\u5177\u3002
         * @param stubbed \u7528\u4e8e\u521d\u59cb\u5316 <code>StubErrorConfiguration</code> \u7684 <code>WebError</code> \u503c
         */
        StubErrorConfiguration(WebError stubbed) {
            this(stubbed, new DefaultWebExceptionTranslator(), WebErrorResponseBuilder.defaults());
        }

        /** {@inheritDoc} */
        @Override
        protected String frameworkName() {
            return "Stub";
        }

        /** {@inheritDoc} */
        @Override
        protected WebError doTranslate(Throwable throwable) {
            return stubbed;
        }
    }

    /**
     * 不覆盖 {@code doTranslate} 的桩基类，验证默认翻译策略接线。
     */
    private static final class PassThroughConfiguration extends BaseErrorConfiguration {

        /** \u4f7f\u7528 <code>super()</code> \u521d\u59cb\u5316 <code>PassThroughConfiguration</code> \u6d4b\u8bd5\u5939\u5177\u3002 */
        PassThroughConfiguration() {
            super();
        }

        /** {@inheritDoc} */
        @Override
        protected String frameworkName() {
            return "Stub";
        }
    }
}
