package io.ddd4j.web.webflux;

import io.ddd4j.web.webflux.config.LocalResourceProperteis;
import io.ddd4j.web.webflux.config.MessageSourceConfigurationProperties;
import org.junit.jupiter.api.Test;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

/** \u901a\u8fc7 <code>MockServerWebExchange.from()</code>\u3001<code>MockServerHttpRequest.get()</code>\u3001<code>context.get()</code> \u9a8c\u8bc1 <code>DefaultWebFluxConfigurationRequestContext</code> \u7684\u8fd0\u884c\u884c\u4e3a\u3002 */
class DefaultWebFluxConfigurationRequestContextTest {

    /** \u9a8c\u8bc1<code>seenExchange</code> \u4e0e\u2026\u4e3a\u540c\u4e00\u5b9e\u4f8b <code>exchange</code>\uff0c<code>context.get(ServerWebExchange.class)</code> \u4e0e\u2026\u4e3a\u540c\u4e00\u5b9e\u4f8b <code>exchange</code>\uff0c<code>context.hasKey(ServerHttpResponse.class)</code>\u4e3a\u5047\u3002 */
    @Test
    void suppliesTheExistingRequestContextKeysOnlyDownstream() {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/orders"));

        new DefaultWebFluxConfiguration().requestContextFilter().filter(exchange,
                seenExchange -> Mono.<Void>deferContextual(context -> {
                    assertSame(exchange, seenExchange);
                    assertSame(exchange, context.get(ServerWebExchange.class));
                    assertSame(exchange.getRequest(), context.get(ServerHttpRequest.class));
                    assertSame(exchange.getResponse(), context.get(ServerHttpResponse.class));
                    assertEquals("present", context.get("outer"));
                    return Mono.empty();
                })).then(Mono.deferContextual(context -> {
            assertFalse(context.hasKey(ServerWebExchange.class));
            assertFalse(context.hasKey(ServerHttpRequest.class));
            assertFalse(context.hasKey(ServerHttpResponse.class));
            return Mono.empty();
        })).contextWrite(context -> context.put("outer", "present")).block();
    }

    /** \u9a8c\u8bc1<code>messages.getBasename()</code> \u7b49\u4e8e <code>Collections.singletonList("messages")</code>\uff0c<code>resources.getLocalLocations()</code> \u7b49\u4e8e <code>Collections.emptyMap()</code>\uff0c<code>messages.getBasename()</code> \u7b49\u4e8e <code>Arrays.asList("messages", "orders")</code>\u3002 */
    @Test
    void usesMutableDefaultsPerConfiguration() {
        MessageSourceConfigurationProperties messages = new MessageSourceConfigurationProperties();
        LocalResourceProperteis resources = new LocalResourceProperteis();
        assertEquals(Collections.singletonList("messages"), messages.getBasename());
        assertEquals(Collections.emptyMap(), resources.getLocalLocations());

        messages.getBasename().add("orders");
        resources.getLocalLocations().put("/docs/**", "/var/docs");

        assertEquals(Arrays.asList("messages", "orders"), messages.getBasename());
        assertEquals(Collections.singletonMap("/docs/**", "/var/docs"), resources.getLocalLocations());
        assertEquals(Collections.singletonList("messages"), new MessageSourceConfigurationProperties().getBasename());
        assertEquals(Collections.emptyMap(), new LocalResourceProperteis().getLocalLocations());
    }
}
