package io.ddd4j.web.error;

import io.ddd4j.core.api.R;
import io.ddd4j.web.core.error.DefaultWebExceptionTranslator;
import io.ddd4j.web.core.error.WebError;
import io.ddd4j.web.core.error.WebExceptionTranslator;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BaseErrorConfigurationTest {

    /**
     * 固定翻译结果的桩基类，便于断言模板方法管线。
     */
    private static final class StubErrorConfiguration extends BaseErrorConfiguration {

        private final WebError stubbed;

        StubErrorConfiguration(WebError stubbed, WebExceptionTranslator translator,
                               WebErrorResponseBuilder responseBuilder) {
            super(translator, responseBuilder);
            this.stubbed = stubbed;
        }

        StubErrorConfiguration(WebError stubbed) {
            this(stubbed, new DefaultWebExceptionTranslator(), WebErrorResponseBuilder.defaults());
        }

        @Override
        protected String frameworkName() {
            return "Stub";
        }

        @Override
        protected WebError doTranslate(Throwable throwable) {
            return stubbed;
        }
    }

    /**
     * 不覆盖 {@code doTranslate} 的桩基类，验证默认翻译策略接线。
     */
    private static final class PassThroughConfiguration extends BaseErrorConfiguration {

        PassThroughConfiguration() {
            super();
        }

        @Override
        protected String frameworkName() {
            return "Stub";
        }
    }

    @Test
    void defaultConstructorUsesDefaultTranslator() {
        BaseErrorConfiguration configuration = new PassThroughConfiguration();

        WebError error = configuration.translate(new IllegalArgumentException("bad"));

        assertEquals(400, error.status());
        assertEquals("bad", error.message());
    }

    @Test
    void rejectsNullTranslatorAndBuilder() {
        assertThrows(NullPointerException.class,
                () -> new StubErrorConfiguration(null, null, WebErrorResponseBuilder.defaults()));
        assertThrows(NullPointerException.class,
                () -> new StubErrorConfiguration(null, new DefaultWebExceptionTranslator(), null));
    }

    @Test
    void doTranslateOverrideShortCircuitsTranslator() {
        WebError stubbed = new WebError(418, "TEAPOT", "short circuited", null);
        BaseErrorConfiguration configuration = new StubErrorConfiguration(stubbed);

        assertEquals(stubbed, configuration.translate(new RuntimeException("ignored")));
    }

    @Test
    void toResponseDelegatesToWebError() {
        WebError error = new WebError(422, "VALIDATION", "bad input", "detail");
        BaseErrorConfiguration configuration = new StubErrorConfiguration(error);

        R<Object> response = configuration.toResponse(error);

        assertEquals("VALIDATION", response.getCode());
        assertEquals("bad input", response.getMsg());
        assertEquals("detail", response.getData());
    }

    @Test
    void toResponseRejectsNullError() {
        BaseErrorConfiguration configuration = new StubErrorConfiguration(null);

        assertThrows(NullPointerException.class, () -> configuration.toResponse(null));
    }

    @Test
    void isServerErrorUsesThreshold500() {
        BaseErrorConfiguration configuration = new StubErrorConfiguration(null);

        assertFalse(configuration.isServerError(new WebError(499, 499, "client", null)));
        assertTrue(configuration.isServerError(new WebError(500, 500, "server", null)));
    }

    @Test
    void logUnhandledToleratesClientAndServerErrors() {
        BaseErrorConfiguration configuration = new StubErrorConfiguration(null);

        configuration.logUnhandled(new Exception("client"), new WebError(400, 400, "client", null));
        configuration.logUnhandled(new Exception("server"), new WebError(500, 500, "server", null));

        assertNotNull(configuration);
    }

    @Test
    void httpStatusErrorFallsBackWhenMessageBlank() {
        WebError error = BaseErrorConfiguration.httpStatusError(404, "Not Found", "  ");

        assertEquals(404, error.status());
        assertEquals(404, error.code());
        assertEquals("Not Found", error.message());
    }

    @Test
    void httpStatusErrorKeepsGivenMessage() {
        WebError error = BaseErrorConfiguration.httpStatusError(409, "Conflict", "duplicate order");

        assertEquals("duplicate order", error.message());
    }

    @Test
    void builderBuildsFallbackBodyAndResponses() {
        WebErrorResponseBuilder builder = WebErrorResponseBuilder.defaults();

        assertArrayEquals(WebErrorResponseBuilder.INTERNAL_ERROR_FALLBACK_JSON.getBytes(StandardCharsets.UTF_8),
                builder.fallbackBody());
        assertEquals(500, builder.toResponse(new WebError(500, 500, "boom", null)).getCode());
        assertTrue(builder.isServerError(new WebError(503, 503, "unavailable", null)));
        assertFalse(builder.isServerError(new WebError(404, 404, "missing", null)));
    }
}
