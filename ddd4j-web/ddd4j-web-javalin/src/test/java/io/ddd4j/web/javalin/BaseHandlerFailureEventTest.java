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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 验证不同 Javalin 版本均将 HTTP 方法写为相同的失败事件字符串。 */
class BaseHandlerFailureEventTest {

    @AfterEach
    void tearDown() {
        BaseContext.remove(SpiKeys.DOMAIN_EVENT_PUBLISHER);
        ThreadContext.clear();
    }

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

    @Test
    void keepsIncomingHttpMethodWhenNativeRoutingOverridesIt() throws IOException {
        DomainEventPublisher publisher = mock(DomainEventPublisher.class);
        TestHandler handler = new TestHandler();
        IllegalStateException failure = new IllegalStateException("PUT route failed");
        Javalin app = Javalin.create(config -> {
            config.startup.showJavalinBanner = false;
            config.routes.put("/orders", context -> { throw failure; });
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

    private static final class TestHandler extends BaseHandler {
        String helpers(Context context) {
            return getMessage("default {}", "request") + ":" + getClientIp(context) + ":" + isAjax(context);
        }

        void emit(Context context, Exception cause) {
            logException(context, cause);
        }
    }
}
