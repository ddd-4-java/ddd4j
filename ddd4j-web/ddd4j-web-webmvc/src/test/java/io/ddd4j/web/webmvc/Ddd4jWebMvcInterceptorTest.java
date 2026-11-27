package io.ddd4j.web.webmvc;

import io.ddd4j.core.context.ThreadContext;
import io.ddd4j.web.core.auth.BearerSubjectAuthenticator;
import io.ddd4j.web.core.context.WebContextScope;
import io.ddd4j.web.core.context.WebHeaders;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** \u9a8c\u8bc1 <code>Ddd4jWebMvcInterceptor</code> \u5728<code>shouldRestoreContext</code>\u7b49\u64cd\u4f5c\u4e2d\u7684\u884c\u4e3a\u5951\u7ea6\u3002 */
class Ddd4jWebMvcInterceptorTest {

    /** \u6d4b\u8bd5\u7ed3\u675f\u540e\u6267\u884c <code>ThreadContext.clear()</code> \u5b8c\u6210\u8d44\u6e90\u56de\u6536\u6216\u72b6\u6001\u6062\u590d\u3002 */
    @AfterEach
    void clearContext() {
        ThreadContext.clear();
    }

    /**
     * \u9a8c\u8bc1<code>interceptor.preHandle(request, response, new Object())</code>\u4e3a\u771f\uff0c<code>ThreadContext.getResources().isEmpty()</code>\u4e3a\u5047\u3002
     * @throws Exception \u5f53\u524d\u7528\u4f8b\u51c6\u5907\u8d44\u6e90\u6216\u8c03\u7528\u6d4b\u8bd5\u5165\u53e3\u5931\u8d25
     */
    @Test
    void shouldBindAndClearPublicRequestContext() throws Exception {
        Ddd4jWebMvcInterceptor interceptor = new Ddd4jWebMvcInterceptor(new BearerSubjectAuthenticator(),
                path -> "/health".equals(path));
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/health");
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertTrue(interceptor.preHandle(request, response, new Object()));
        assertFalse(ThreadContext.getResources().isEmpty());
        assertEquals(ThreadContext.get(WebContextScope.REQUEST_ID, String.class).orElseThrow(),
                response.getHeader(WebHeaders.REQUEST_ID));

        interceptor.afterCompletion(request, response, new Object(), null);
        assertTrue(ThreadContext.getResources().isEmpty());
    }
}
