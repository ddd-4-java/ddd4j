package io.ddd4j.web.webflux;

import io.ddd4j.web.core.auth.BearerSubjectAuthenticator;
import io.ddd4j.web.core.observability.WebOtelSupport;
import org.junit.jupiter.api.Test;
import org.mockito.MockMakers;
import org.mockito.MockedStatic;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.withSettings;

/**
 * 无可选追踪时真实交换属性不接受 null，且业务失败仍保持原异常。
 */
class Ddd4jWebFluxOptionalTracingTest {
    @Test
    void absentSpanMustNotBreakPublicRequestOrMaskBusinessFailure() {
        try (MockedStatic<WebOtelSupport> telemetry = mockStatic(
                WebOtelSupport.class, withSettings().mockMaker(MockMakers.INLINE))) {
            Ddd4jWebFluxFilter filter = new Ddd4jWebFluxFilter(new BearerSubjectAuthenticator(), path -> true);
            MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/orders"));
            filter.filter(exchange, ignored -> Mono.empty()).block();
            assertFalse(exchange.getAttributes().containsKey(Ddd4jWebFluxFilter.class.getName() + ".otelSpan"));
            IllegalArgumentException businessFailure = new IllegalArgumentException("business");
            MockServerWebExchange failedExchange = MockServerWebExchange.from(MockServerHttpRequest.get("/orders"));
            IllegalArgumentException actual = assertThrows(IllegalArgumentException.class,
                    () -> filter.filter(failedExchange, ignored -> Mono.error(businessFailure)).block());
            assertSame(businessFailure, actual);
        }
    }
}
