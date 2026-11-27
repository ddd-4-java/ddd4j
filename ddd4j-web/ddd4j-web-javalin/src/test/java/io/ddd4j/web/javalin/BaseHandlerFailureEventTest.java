package io.ddd4j.web.javalin;

import io.ddd4j.core.constant.SpiKeys;
import io.ddd4j.core.context.BaseContext;
import io.ddd4j.core.context.SpiRegistrationScope;
import io.ddd4j.core.context.ThreadContext;
import io.ddd4j.core.ddd.event.DomainEventPublisher;
import io.ddd4j.core.i18n.I18nProvider;
import io.ddd4j.web.core.context.WebRequestFailure;
import io.javalin.Javalin;
import io.javalin.http.Context;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * 验证不同 Javalin 版本均将 HTTP 方法写为相同的失败事件字符串。
 */
class BaseHandlerFailureEventTest {

    /**
     * \u901a\u8fc7 <code>status + ":" + reader.readLine()</code> \u53d6\u5f97\u5f53\u524d\u6d4b\u8bd5\u9700\u8981\u7684\u7ed3\u679c\u3002
     * @param port \u6d4b\u8bd5\u670d\u52a1\u76d1\u542c\u7684\u672c\u5730\u7aef\u53e3
     * @param method \u53d1\u7ed9\u88ab\u6d4b\u670d\u52a1\u7684 HTTP \u65b9\u6cd5
     * @param path \u6d4b\u8bd5\u8bf7\u6c42\u7684\u8d44\u6e90\u8def\u5f84\u6216\u5f85\u8bbf\u95ee\u6587\u4ef6\u8def\u5f84
     * @param override \u4f20\u7ed9 <code>Objects.nonNull()</code> \u7684 <code>String</code> \u8f93\u5165
     * @return \u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u7ed3\u679c\uff0c\u53d6\u81ea <code>status + ":" + reader.readLine()</code>
     * @throws IOException \u8bfb\u53d6\u6216\u5199\u5165\u6d4b\u8bd5\u6570\u636e\u65f6\u53d1\u751f I/O \u9519\u8bef
     */
    private static String request(int port, String method, String path, String override) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL("http://127.0.0.1:" + port + path).openConnection();
        try {
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);
            connection.setRequestMethod(method);
            connection.setRequestProperty("X-Forwarded-For", "198.51.100.7, 198.51.100.8");
            connection.setRequestProperty("X-Requested-With", "xmlhttprequest");
            if (Objects.nonNull(override)) {
                connection.setRequestProperty("X-HTTP-Method-Override", override);
            }
            int status = connection.getResponseCode();
            InputStream response = status >= 400 ? connection.getErrorStream() : connection.getInputStream();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(response, StandardCharsets.UTF_8))) {
                return status + ":" + reader.readLine();
            }
        } finally {
            connection.disconnect();
        }
    }

    /** \u6d4b\u8bd5\u7ed3\u675f\u540e\u6267\u884c <code>BaseContext.remove()</code>\u3001<code>ThreadContext.clear()</code> \u5b8c\u6210\u8d44\u6e90\u56de\u6536\u6216\u72b6\u6001\u6062\u590d\u3002 */
    @AfterEach
    void tearDown() {
        BaseContext.remove(SpiKeys.DOMAIN_EVENT_PUBLISHER);
        ThreadContext.clear();
    }

    /** \u9a8c\u8bc1<code>captured.getValue() instanceof WebRequestFailure</code>\u4e3a\u771f\uff0c<code>event.getMethod()</code> \u7b49\u4e8e <code>"POST"</code>\uff0c<code>event.getCause()</code> \u4e0e\u2026\u4e3a\u540c\u4e00\u5b9e\u4f8b <code>cause</code>\u3002 */
    @Test
    void publishesRequestFailureWithStableHttpMethod() {
        DomainEventPublisher publisher = mock(DomainEventPublisher.class);
        BaseContext.inject(SpiKeys.DOMAIN_EVENT_PUBLISHER, DomainEventPublisher.class, publisher);
        Context context = mock(Context.class);
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(context.req()).thenReturn(request);
        when(request.getMethod()).thenReturn("POST");
        when(context.path()).thenReturn("/orders");
        Exception cause = new IllegalStateException("failure");

        new TestHandler().emit(context, cause);

        ArgumentCaptor<Object> captured = ArgumentCaptor.forClass(Object.class);
        verify(publisher).publish(captured.capture());
        assertTrue(captured.getValue() instanceof WebRequestFailure);
        WebRequestFailure event = (WebRequestFailure) captured.getValue();
        assertEquals("POST", event.getMethod());
        assertEquals("/orders", event.getPath());
        assertSame(cause, event.getCause());
    }

    /**
     * \u9a8c\u8bc1<code>request(app.port(), "GET", "/helpers", null)</code> \u7b49\u4e8e <code>"200:custom request:198.51.100.7:true"</code>\uff0c<code>request(app.port(), "GET", "/helpers", null)</code> \u7b49\u4e8e <code>"200:default request:198.51.100.7:true"</code>\uff0c<code>event.getCause()</code> \u4e0e\u2026\u4e3a\u540c\u4e00\u5b9e\u4f8b <code>failure</code>\u3002
     * @throws IOException \u8bfb\u53d6\u6216\u5199\u5165\u6d4b\u8bd5\u6570\u636e\u65f6\u53d1\u751f I/O \u9519\u8bef
     */
    @Test
    void keepsIncomingHttpMethodWhenNativeRoutingOverridesIt() throws IOException {
        DomainEventPublisher publisher = mock(DomainEventPublisher.class);
        TestHandler handler = new TestHandler();
        IllegalStateException failure = new IllegalStateException("PUT route failed");
        Javalin app = Javalin.create(config -> {
            config.startup.showJavalinBanner = false;
            config.routes.put("/orders", context -> {
                throw failure;
            });
            config.routes.get("/helpers", context -> context.result(handler.helpers(context)));
            config.routes.exception(IllegalStateException.class, (cause, context) -> {
                handler.emit(context, cause);
                context.status(409).result(cause.getMessage());
            });
        });
        try {
            app.start("127.0.0.1", 0);
            try (SpiRegistrationScope services = new SpiRegistrationScope()
                    .register(SpiKeys.DOMAIN_EVENT_PUBLISHER, DomainEventPublisher.class, publisher)
                    .register(SpiKeys.I18N_PROVIDER, I18nProvider.class,
                            (key, args) -> "custom " + args[0])) {
                services.start();
                assertEquals("200:custom request:198.51.100.7:true", request(app.port(), "GET", "/helpers", null));
                assertEquals("409:PUT route failed", request(app.port(), "POST", "/orders", "PUT"));
            }
            assertEquals("200:default request:198.51.100.7:true", request(app.port(), "GET", "/helpers", null));
            assertEquals("409:PUT route failed", request(app.port(), "PUT", "/orders", null));

            ArgumentCaptor<Object> captured = ArgumentCaptor.forClass(Object.class);
            verify(publisher).publish(captured.capture());
            assertTrue(captured.getValue() instanceof WebRequestFailure);
            WebRequestFailure event = (WebRequestFailure) captured.getValue();
            assertEquals("POST", event.getMethod());
            assertEquals("/orders", event.getPath());
            assertSame(failure, event.getCause());
        } finally {
            app.stop();
        }
    }

    /** \u5b9e\u73b0 <code>BaseHandler</code>\uff0c\u4f9b\u6d4b\u8bd5\u8c03\u7528 <code>helpers()</code>\u3001<code>emit()</code> \u5e76\u89c2\u5bdf\u7ed3\u679c\u3002 */
    private static final class TestHandler extends BaseHandler {
        /**
         * \u901a\u8fc7 <code>getMessage()</code> \u53d6\u5f97\u5f53\u524d\u6d4b\u8bd5\u9700\u8981\u7684\u7ed3\u679c\u3002
         * @param context \u5f53\u524d\u6846\u67b6\u4fdd\u5b58\u8bf7\u6c42\u6216\u8fd0\u884c\u65f6\u72b6\u6001\u7684\u4e0a\u4e0b\u6587
         * @return \u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u7ed3\u679c\uff0c\u53d6\u81ea <code>getMessage("default {}", "request") + ":" + getClientIp(context) + ":" + isAjax(context)</code>
         */
        String helpers(Context context) {
            return getMessage("default {}", "request") + ":" + getClientIp(context) + ":" + isAjax(context);
        }

        /**
         * \u8c03\u7528 <code>logException()</code> \u6267\u884c\u5f53\u524d\u5939\u5177\u64cd\u4f5c\u3002
         * @param context \u5f53\u524d\u6846\u67b6\u4fdd\u5b58\u8bf7\u6c42\u6216\u8fd0\u884c\u65f6\u72b6\u6001\u7684\u4e0a\u4e0b\u6587
         * @param cause \u4f20\u7ed9 <code>logException()</code> \u7684 <code>Exception</code> \u8f93\u5165
         */
        void emit(Context context, Exception cause) {
            logException(context, cause);
        }
    }
}
