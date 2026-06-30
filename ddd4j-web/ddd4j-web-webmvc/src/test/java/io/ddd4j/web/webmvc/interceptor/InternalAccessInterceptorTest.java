package io.ddd4j.web.webmvc.interceptor;

import io.ddd4j.annotation.api.InternalAccess;
import io.ddd4j.web.webmvc.config.InternalAccessProperties;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.resource.ResourceHttpRequestHandler;

import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

/**
 * {@link InternalAccessInterceptor} tests.
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
class InternalAccessInterceptorTest {

    private static InternalAccessInterceptor interceptor(String token) {
        InternalAccessProperties properties = new InternalAccessProperties();
        properties.setBearerTokens(List.of(token));
        return new InternalAccessInterceptor(properties);
    }

    private static HttpServletRequest request(String authorization) {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader("Authorization")).thenReturn(authorization);
        return request;
    }

    private static HandlerMethod handler(String methodName) throws Exception {
        SampleController controller = new SampleController();
        Method method = SampleController.class.getDeclaredMethod(methodName);
        return new HandlerMethod(controller, method);
    }

    @Test
    void bearerTokenHitShouldPass() throws Exception {
        InternalAccessInterceptor interceptor = interceptor("secret");
        HttpServletRequest request = request("Bearer secret");
        HttpServletResponse response = mock(HttpServletResponse.class);

        assertTrue(interceptor.preHandle(request, response, handler("normal")));
    }

    @Test
    void missingBearerTokenShouldReject() throws Exception {
        InternalAccessInterceptor interceptor = interceptor("secret");
        HttpServletRequest request = request(null);
        HttpServletResponse response = mock(HttpServletResponse.class);

        assertFalse(interceptor.preHandle(request, response, handler("normal")));

        verify(response).sendError(HttpServletResponse.SC_UNAUTHORIZED, "Unauthorized internal access");
    }

    @Test
    void wrongBearerTokenShouldReject() throws Exception {
        InternalAccessInterceptor interceptor = interceptor("secret");
        HttpServletRequest request = request("Bearer wrong");
        HttpServletResponse response = mock(HttpServletResponse.class);

        assertFalse(interceptor.preHandle(request, response, handler("normal")));
    }

    @Test
    void internalAccessAnnotationShouldSkipCheck() throws Exception {
        InternalAccessInterceptor interceptor = interceptor("secret");
        HttpServletRequest request = request(null);
        HttpServletResponse response = mock(HttpServletResponse.class);

        assertTrue(interceptor.preHandle(request, response, handler("internal")));
    }

    @Test
    void staticResourceShouldSkipCheck() throws Exception {
        InternalAccessInterceptor interceptor = interceptor("secret");
        HttpServletRequest request = request(null);
        HttpServletResponse response = mock(HttpServletResponse.class);

        assertTrue(interceptor.preHandle(request, response, new ResourceHttpRequestHandler()));
    }

    static class SampleController {
        void normal() {
        }

        @InternalAccess
        void internal() {
        }
    }
}
