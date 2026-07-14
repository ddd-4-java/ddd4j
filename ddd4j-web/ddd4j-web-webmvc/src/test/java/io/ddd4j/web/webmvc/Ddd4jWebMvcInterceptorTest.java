package io.ddd4j.web.webmvc;

import io.ddd4j.core.context.ThreadContext;
import io.ddd4j.web.core.BearerSubjectAuthenticator;
import io.ddd4j.web.core.WebContextScope;
import io.ddd4j.web.core.WebHeaders;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Ddd4jWebMvcInterceptorTest {

    @AfterEach
    void clearContext() {
        ThreadContext.clear();
    }

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
