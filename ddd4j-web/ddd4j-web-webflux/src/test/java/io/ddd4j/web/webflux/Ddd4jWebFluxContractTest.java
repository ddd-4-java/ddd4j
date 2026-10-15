/*
 * Copyright (c) 2024-2026 ddd4j project. All rights reserved.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.ddd4j.web.webflux;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.ddd4j.extension.otel.Ddd4jOtel;
import io.ddd4j.web.core.auth.BearerSubjectAuthenticator;
import io.ddd4j.web.core.auth.WebAccessPolicy;
import io.ddd4j.web.core.context.WebHeaders;
import io.ddd4j.web.core.context.WebRequestContext;
import io.ddd4j.web.core.context.WebRequestContextFactory;
import io.ddd4j.web.core.context.WebRequestLifecycle;
import io.ddd4j.web.core.idempotency.WebIdempotencyLifecycle;
import io.ddd4j.web.core.idempotency.IdempotencyGuard;
import io.ddd4j.web.core.error.DefaultWebExceptionTranslator;
import io.ddd4j.web.webflux.error.GlobalErrorAttributes;
import io.ddd4j.web.webflux.error.GlobalErrorWebExceptionHandler;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.core.Disposable;
import reactor.core.scheduler.Schedulers;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.CountDownLatch;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class Ddd4jWebFluxContractTest {

    private SdkTracerProvider tracerProvider;

    private static void resetOpenTelemetry() throws Exception {
        GlobalOpenTelemetry.resetForTest();
        Class<?> ddd4jOtel = Class.forName("io.ddd4j.extension.otel.Ddd4jOtel");
        for (String fieldName : new String[]{"TRACER_CACHE", "METER_CACHE"}) {
            Field field = ddd4jOtel.getDeclaredField(fieldName);
            field.setAccessible(true);
            ((AtomicReference<?>) field.get(null)).set(null);
        }
        Field available = ddd4jOtel.getDeclaredField("available");
        available.setAccessible(true);
        available.setBoolean(null, false);
    }

    @BeforeEach
    void setUpOpenTelemetry() throws Exception {
        resetOpenTelemetry();
        tracerProvider = SdkTracerProvider.builder().build();
        OpenTelemetrySdk.builder()
                .setTracerProvider(tracerProvider)
                .setPropagators(ContextPropagators.create(W3CTraceContextPropagator.getInstance()))
                .buildAndRegisterGlobal();
    }

    @AfterEach
    void tearDownOpenTelemetry() throws Exception {
        if (tracerProvider != null) {
            tracerProvider.close();
        }
        resetOpenTelemetry();
    }

    @Test
    void shouldPropagateRequestThroughReactorContext() {
        Ddd4jWebFluxFilter filter = new Ddd4jWebFluxFilter(new BearerSubjectAuthenticator(),
                path -> "/health".equals(path));
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/health").header(WebHeaders.TENANT_ID, "tenant-a"));
        CompletableFuture<WebRequestContext> captured = new CompletableFuture<>();

        filter.filter(exchange, ignored -> Ddd4jWebFluxContext.currentRequest()
                .doOnNext(captured::complete).then()).block();

        WebRequestContext requestContext = captured.join();
        assertNotNull(requestContext);
        assertEquals("tenant-a", requestContext.tenantId());
        assertEquals(requestContext.requestId(), exchange.getResponse().getHeaders().getFirst(WebHeaders.REQUEST_ID));
    }

    @Test
    void shouldUseTranslatedHttpStatusAndResponseBody() {
        GlobalErrorWebExceptionHandler handler = new GlobalErrorWebExceptionHandler(new GlobalErrorAttributes(),
                new ObjectMapper(), new DefaultWebExceptionTranslator());
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/orders"));

        handler.handle(exchange, new IllegalArgumentException("invalid order")).block();

        assertEquals(HttpStatus.BAD_REQUEST, exchange.getResponse().getStatusCode());
        String body = exchange.getResponse().getBodyAsString().block();
        assertNotNull(body);
        assertTrue(body.contains("invalid order"));
        assertTrue(body.contains("400"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldExtractRequestHeadersForTracePropagation() throws Exception {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/health")
                .header("traceparent", "00-0af7651916cd43dd8448eb211c80319c-b7ad6b7169203331-01")
                .header("X-Multi", "first", "second"));
        Method extractHeaders = Ddd4jWebFluxFilter.class.getDeclaredMethod(
                "extractHeaders", org.springframework.web.server.ServerWebExchange.class);
        extractHeaders.setAccessible(true);

        Map<String, String> headers = (Map<String, String>) extractHeaders.invoke(null, exchange);

        assertEquals("00-0af7651916cd43dd8448eb211c80319c-b7ad6b7169203331-01",
                headers.get("traceparent"));
        assertEquals("first", headers.get("X-Multi"));
    }

    @Test
    void shouldActivateServerSpanOnlyAtWebFluxChainBoundary() {
        assertTrue(Ddd4jOtel.isAvailable());
        Ddd4jWebFluxFilter filter = new Ddd4jWebFluxFilter(
                new WebRequestContextFactory(),
                new WebRequestLifecycle(new BearerSubjectAuthenticator(), WebAccessPolicy.disabled()),
                null, Schedulers.immediate());
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/health"));
        AtomicBoolean chainObservedValidSpan = new AtomicBoolean();

        Mono<Void> invocation = filter.filter(exchange, ignored -> {
            chainObservedValidSpan.set(Span.current().getSpanContext().isValid());
            return Mono.empty();
        });
        boolean assemblyThreadLeaked = Span.current().getSpanContext().isValid();
        invocation.block();

        assertFalse(assemblyThreadLeaked);
        assertTrue(chainObservedValidSpan.get());
        assertFalse(Span.current().getSpanContext().isValid());
    }
    @Test
    void shouldReleaseIdempotencyLeaseWhenCancelledDuringAcquisition() throws InterruptedException {
        CountDownLatch acquiring = new CountDownLatch(1);
        CountDownLatch finishAcquisition = new CountDownLatch(1);
        CountDownLatch acquired = new CountDownLatch(1);
        CountDownLatch released = new CountDownLatch(1);
        IdempotencyGuard guard = new IdempotencyGuard() {
            @Override
            public boolean acquire(String key, Duration ttl) {
                acquiring.countDown();
                while (finishAcquisition.getCount() > 0) {
                    try {
                        finishAcquisition.await();
                    } catch (InterruptedException ignored) {
                        // Simulate a non-interruptible remote acquire that still grants a lease.
                    }
                }
                acquired.countDown();
                return true;
            }

            @Override
            public void complete(String key) {
            }

            @Override
            public void release(String key) {
                released.countDown();
            }
        };
        Ddd4jWebFluxFilter filter = new Ddd4jWebFluxFilter(
                new WebRequestContextFactory(),
                new WebRequestLifecycle(new BearerSubjectAuthenticator(), WebAccessPolicy.disabled()),
                new WebIdempotencyLifecycle(guard), Schedulers.boundedElastic());
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.post("/orders")
                .header(WebHeaders.IDEMPOTENCY_KEY, "cancel-during-acquire"));

        Disposable subscription = filter.filter(exchange, ignored -> Mono.<Void>never()).subscribe();
        try {
            assertTrue(acquiring.await(3, TimeUnit.SECONDS));
            subscription.dispose();
            finishAcquisition.countDown();
            assertTrue(acquired.await(3, TimeUnit.SECONDS));
            assertTrue(released.await(3, TimeUnit.SECONDS));
        } finally {
            finishAcquisition.countDown();
            subscription.dispose();
        }
    }


}
