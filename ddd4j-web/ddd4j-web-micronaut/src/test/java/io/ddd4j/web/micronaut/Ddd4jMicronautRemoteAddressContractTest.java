package io.ddd4j.web.micronaut;

import io.ddd4j.web.core.auth.BearerSubjectAuthenticator;
import io.ddd4j.web.core.auth.WebAccessPolicy;
import io.ddd4j.web.core.context.ClientIpResolver;
import io.ddd4j.web.core.context.WebRequestContext;
import io.ddd4j.web.core.context.WebRequestContextFactory;
import io.ddd4j.web.core.context.WebRequestLifecycle;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpRequest;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.util.Locale;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** 三线 Micronaut 请求地址缺失及未解析时的共同上下文契约。 */
class Ddd4jMicronautRemoteAddressContractTest {

    private WebRequestContext contextFor(InetSocketAddress remoteAddress) throws Exception {
        Ddd4jMicronautWebFilter filter = new Ddd4jMicronautWebFilter(
                new WebRequestContextFactory(() -> "request-1", ClientIpResolver.remoteAddressOnly()),
                new WebRequestLifecycle(new BearerSubjectAuthenticator(), WebAccessPolicy.disabled()), null);
        HttpRequest<?> request = mock(HttpRequest.class);
        HttpHeaders headers = mock(HttpHeaders.class);
        when(request.getHeaders()).thenReturn(headers);
        when(request.getLocale()).thenReturn(Optional.of(Locale.ROOT));
        when(request.getMethodName()).thenReturn("GET");
        when(request.getPath()).thenReturn("/edge");
        when(request.getRemoteAddress()).thenReturn(remoteAddress);
        Method createContext = Ddd4jMicronautWebFilter.class.getDeclaredMethod("createContext", HttpRequest.class);
        createContext.setAccessible(true);
        return (WebRequestContext) createContext.invoke(filter, request);
    }

    @Test
    void absentRemoteAddressUsesUnknown() throws Exception {
        assertEquals("unknown", contextFor(null).clientIp());
    }

    @Test
    void unresolvedRemoteAddressUsesUnknown() throws Exception {
        assertEquals("unknown", contextFor(InetSocketAddress.createUnresolved("example.test", 8080)).clientIp());
    }
}
