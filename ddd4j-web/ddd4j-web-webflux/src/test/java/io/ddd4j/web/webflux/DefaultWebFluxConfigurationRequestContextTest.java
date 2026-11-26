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

class DefaultWebFluxConfigurationRequestContextTest {

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
