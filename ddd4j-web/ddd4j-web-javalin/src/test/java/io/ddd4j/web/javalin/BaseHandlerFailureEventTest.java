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

    /** \u5b9e\u73b0 <code>BaseHandler</code>\uff0c\u4f9b\u6d4b\u8bd5\u8c03\u7528 <code>helpers()</code>\u3001<code>emit()</code> \u5e76\u89c2\u5bdf\u7ed3\u679c\u3002 */
    private static final class TestHandler extends BaseHandler {
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
