package io.ddd4j.web.javalin;

import io.ddd4j.core.constant.SpiKeys;
import io.ddd4j.core.context.BaseContext;
import io.ddd4j.core.context.ThreadContext;
import io.ddd4j.core.ddd.event.DomainEventPublisher;
import io.ddd4j.web.core.context.WebRequestFailure;
import io.javalin.http.Context;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Collections;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

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
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getMethod()).thenReturn("POST");
        when(request.getRequestURI()).thenReturn("/orders");
        Context context = new Context(request, mock(HttpServletResponse.class), Collections.emptyMap());
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

    private static final class TestHandler extends BaseHandler {
        void emit(Context context, Exception cause) {
            logException(context, cause);
        }
    }
}
