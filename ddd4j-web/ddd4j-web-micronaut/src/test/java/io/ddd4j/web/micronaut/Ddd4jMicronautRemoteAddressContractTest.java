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

    /**
     * \u901a\u8fc7 <code>(WebRequestContext)createContext.invoke(filter, request)</code> \u53d6\u5f97\u5f53\u524d\u6d4b\u8bd5\u9700\u8981\u7684\u7ed3\u679c\u3002
     * @param remoteAddress \u4f20\u7ed9 <code>when()</code> \u7684 <code>InetSocketAddress</code> \u8f93\u5165
     * @return \u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u7ed3\u679c\uff0c\u53d6\u81ea <code>(WebRequestContext)createContext.invoke(filter, request)</code>
     * @throws Exception \u53cd\u5c04\u8bbf\u95ee\u88ab\u6d4b\u6210\u5458\u65f6\u5931\u8d25
     */
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

    /**
     * \u9a8c\u8bc1<code>contextFor(null).clientIp()</code> \u7b49\u4e8e <code>"unknown"</code>\u3002
     * @throws Exception \u5f53\u524d\u7528\u4f8b\u51c6\u5907\u8d44\u6e90\u6216\u8c03\u7528\u6d4b\u8bd5\u5165\u53e3\u5931\u8d25
     */
    @Test
    void absentRemoteAddressUsesUnknown() throws Exception {
        assertEquals("unknown", contextFor(null).clientIp());
    }

    /**
     * \u9a8c\u8bc1<code>contextFor(InetSocketAddress.createUnresolved("example.test", 8080)).clientIp()</code> \u7b49\u4e8e <code>"unknown"</code>\u3002
     * @throws Exception \u5f53\u524d\u7528\u4f8b\u51c6\u5907\u8d44\u6e90\u6216\u8c03\u7528\u6d4b\u8bd5\u5165\u53e3\u5931\u8d25
     */
    @Test
    void unresolvedRemoteAddressUsesUnknown() throws Exception {
        assertEquals("unknown", contextFor(InetSocketAddress.createUnresolved("example.test", 8080)).clientIp());
    }
}
