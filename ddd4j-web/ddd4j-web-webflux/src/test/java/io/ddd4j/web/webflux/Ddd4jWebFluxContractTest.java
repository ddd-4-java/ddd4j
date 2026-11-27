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
import io.ddd4j.core.auth.AuthPrincipal;
import io.ddd4j.core.constant.SpiKeys;
import io.ddd4j.core.context.BaseContext;
import io.ddd4j.core.subject.Subject;
import io.ddd4j.core.subject.SubjectProvider;
import io.ddd4j.extension.otel.Ddd4jOtel;
import io.ddd4j.extension.otel.HttpSpan;
import io.ddd4j.web.core.auth.BearerSubjectAuthenticator;
import io.ddd4j.web.core.auth.WebAccessPolicy;
import io.ddd4j.web.core.context.WebHeaders;
import io.ddd4j.web.core.context.WebRequestContext;
import io.ddd4j.web.core.context.WebRequestContextFactory;
import io.ddd4j.web.core.context.WebRequestLifecycle;
import io.ddd4j.web.core.error.DefaultWebExceptionTranslator;
import io.ddd4j.web.core.error.WebError;
import io.ddd4j.web.core.error.WebStatusException;
import io.ddd4j.web.core.idempotency.IdempotencyGuard;
import io.ddd4j.web.core.idempotency.WebIdempotencyLifecycle;
import io.ddd4j.web.webflux.error.GlobalErrorAttributes;
import io.ddd4j.web.webflux.error.GlobalErrorWebExceptionHandler;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.trace.ReadWriteSpan;
import io.opentelemetry.sdk.trace.ReadableSpan;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.SpanProcessor;
import io.opentelemetry.sdk.trace.data.SpanData;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.handler.ExceptionHandlingWebHandler;
import reactor.core.Disposable;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.time.Duration;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** \u9a8c\u8bc1 <code>Ddd4jWebFluxContract</code> \u5728 <code>shouldWriteCustomHttpStatus</code>\u3001<code>shouldReleaseIdempotencyLease</code> \u7b49\u573a\u666f\u4e2d\u7684\u884c\u4e3a\u3002 */
class Ddd4jWebFluxContractTest {

    /** \u89c2\u5bdf\u5f53\u524d\u8bf7\u6c42\u7ed3\u675f\u65f6\u5173\u95ed\u7684 OpenTelemetry Span\u3002 */
    private final CompletableFuture<SpanData> endedSpan = new CompletableFuture<>();
    /** \u4e3a\u6d4b\u8bd5\u6536\u96c6 OpenTelemetry \u8ddf\u8e2a\u4fe1\u606f\u7684\u63d0\u4f9b\u5668\u3002 */
    private SdkTracerProvider tracerProvider;

    /**
     * \u8c03\u7528 <code>GlobalOpenTelemetry.resetForTest()</code>\u3001<code>Class.forName()</code>\u3001<code>ddd4jOtel.getDeclaredField()</code> \u6267\u884c\u5f53\u524d\u5939\u5177\u64cd\u4f5c\u3002
     * @throws Exception \u53cd\u5c04\u8bbf\u95ee\u88ab\u6d4b\u6210\u5458\u65f6\u5931\u8d25
     */
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

    /**
     * \u6d4b\u8bd5\u6267\u884c\u524d\u6267\u884c <code>resetOpenTelemetry()</code>\u3001<code>SdkTracerProvider.builder()</code>\u3001<code>OpenTelemetrySdk.builder()</code>\u3001<code>ContextPropagators.create()</code> \u5b8c\u6210\u8d44\u6e90\u51c6\u5907\u3002
     * @throws Exception \u5f53\u524d\u7528\u4f8b\u51c6\u5907\u8d44\u6e90\u6216\u8c03\u7528\u6d4b\u8bd5\u5165\u53e3\u5931\u8d25
     */
    @BeforeEach
    void setUpOpenTelemetry() throws Exception {
        resetOpenTelemetry();
        tracerProvider = SdkTracerProvider.builder()
                .addSpanProcessor(new SpanProcessor() {
                    @Override
                    public void onStart(Context parentContext, ReadWriteSpan span) {
                    }

                    @Override
                    public boolean isStartRequired() {
                        return false;
                    }

                    @Override
                    public void onEnd(ReadableSpan span) {
                        endedSpan.complete(span.toSpanData());
                    }

                    @Override
                    public boolean isEndRequired() {
                        return true;
                    }
                }).build();
        OpenTelemetrySdk.builder()
                .setTracerProvider(tracerProvider)
                .setPropagators(ContextPropagators.create(W3CTraceContextPropagator.getInstance()))
                .buildAndRegisterGlobal();
    }

    /**
     * \u6d4b\u8bd5\u7ed3\u675f\u540e\u6267\u884c <code>tracerProvider.close()</code>\u3001<code>resetOpenTelemetry()</code> \u5b8c\u6210\u8d44\u6e90\u56de\u6536\u6216\u72b6\u6001\u6062\u590d\u3002
     * @throws Exception \u5f53\u524d\u7528\u4f8b\u51c6\u5907\u8d44\u6e90\u6216\u8c03\u7528\u6d4b\u8bd5\u5165\u53e3\u5931\u8d25
     */
    @AfterEach
    void tearDownOpenTelemetry() throws Exception {
        if (tracerProvider != null) {
            tracerProvider.close();
        }
        resetOpenTelemetry();
    }

    /** \u9a8c\u8bc1<code>requestContext</code>\u975e\u7a7a\uff0c<code>requestContext.tenantId()</code> \u7b49\u4e8e <code>"tenant-a"</code>\uff0c<code>exchange.getResponse().getHeaders().getFirst(WebHeaders.REQUEST_ID)</code> \u7b49\u4e8e <code>requestContext.requestId()</code>\u3002 */
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

    /** \u9a8c\u8bc1<code>exchange.getResponse().getStatusCode()</code> \u7b49\u4e8e <code>HttpStatus.BAD_REQUEST</code>\uff0c<code>body</code>\u975e\u7a7a\uff0c<code>body.contains("400")</code>\u4e3a\u771f\u3002 */
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

    /**
     * \u9a8c\u8bc1<code>exchange.getResponse().getStatusCode().value()</code> \u7b49\u4e8e <code>599</code>\uff0c<code>body</code>\u975e\u7a7a\uff0c<code>span.getStatus().getStatusCode()</code> \u7b49\u4e8e <code>StatusCode.ERROR</code>\u3002
     * @throws Exception \u7b49\u5f85\u5e76\u53d1\u64cd\u4f5c\u65f6\u5931\u8d25
     */
    @Test
    void shouldWriteCustomHttpStatusWithoutLosingErrorBody() throws Exception {
        GlobalErrorWebExceptionHandler handler = new GlobalErrorWebExceptionHandler(new GlobalErrorAttributes(),
                new ObjectMapper(), ignored -> new WebError(599, 599, "custom status", null));
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/orders"));

        Ddd4jWebFluxFilter filter = new Ddd4jWebFluxFilter(new BearerSubjectAuthenticator(), path -> true);
        IllegalStateException failure = new IllegalStateException("custom status");
        ExceptionHandlingWebHandler pipeline = new ExceptionHandlingWebHandler(
                request -> filter.filter(request, ignored -> Mono.error(failure)), Collections.singletonList(handler));

        pipeline.handle(exchange).block(Duration.ofSeconds(5));
        assertEquals(599, exchange.getResponse().getStatusCode().value());

        String body = exchange.getResponse().getBodyAsString().block();
        assertNotNull(body);
        assertTrue(body.contains("599"));
        assertTrue(body.contains("custom status"));

        SpanData span = endedSpan.get(5, TimeUnit.SECONDS);
        assertEquals("599", span.getAttributes().get(HttpSpan.ATTR_HTTP_STATUS));
        assertEquals(StatusCode.ERROR, span.getStatus().getStatusCode());
    }

    /**
     * \u9a8c\u8bc1<code>headers.get("traceparent")</code> \u7b49\u4e8e <code>"00-0af7651916cd43dd8448eb211c80319c-b7ad6b7169203331-01"</code>\uff0c<code>headers.get("X-Multi")</code> \u7b49\u4e8e <code>"first"</code>\u3002
     * @throws Exception \u53cd\u5c04\u8bbf\u95ee\u88ab\u6d4b\u6210\u5458\u65f6\u5931\u8d25
     */
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

    /** \u9a8c\u8bc1<code>Ddd4jOtel.isAvailable()</code>\u4e3a\u771f\uff0c<code>assemblyThreadLeaked</code>\u4e3a\u5047\uff0c<code>Span.current().getSpanContext().isValid()</code>\u4e3a\u5047\u3002 */
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

    /**
     * \u9a8c\u8bc1<code>firstContext.get()</code>\u975e\u7a7a\uff0c<code>firstContext.get().tenantId()</code> \u7b49\u4e8e <code>"tenant-a"</code>\uff0c<code>Ddd4jWebFluxContext.currentSubject().block()</code>\u4e3a\u7a7a\u3002
     * @throws InterruptedException \u7b49\u5f85\u6d4b\u8bd5\u64cd\u4f5c\u65f6\u88ab\u4e2d\u65ad
     */
    @Test
    void shouldReleaseIdempotencyLeaseAndRequestContextOnCancellation() throws InterruptedException {
        AtomicInteger completed = new AtomicInteger();
        AtomicInteger released = new AtomicInteger();
        CountDownLatch releaseSignal = new CountDownLatch(1);
        IdempotencyGuard guard = new IdempotencyGuard() {
            @Override
            public boolean acquire(String key, Duration ttl) {
                return true;
            }

            @Override
            public void complete(String key) {
                completed.incrementAndGet();
            }

            @Override
            public void release(String key) {
                released.incrementAndGet();
                releaseSignal.countDown();
            }
        };
        Ddd4jWebFluxFilter filter = new Ddd4jWebFluxFilter(
                new WebRequestContextFactory(),
                new WebRequestLifecycle(new BearerSubjectAuthenticator(), WebAccessPolicy.disabled()),
                new WebIdempotencyLifecycle(guard), Schedulers.immediate());
        AtomicReference<WebRequestContext> firstContext = new AtomicReference<>();
        MockServerWebExchange first = MockServerWebExchange.from(MockServerHttpRequest.get("/orders")
                .header(WebHeaders.TENANT_ID, "tenant-a")
                .header(WebHeaders.IDEMPOTENCY_KEY, "cancelled-request"));

        Disposable subscription = filter.filter(first, ignored -> Ddd4jWebFluxContext.currentRequest()
                .doOnNext(firstContext::set).then(Mono.<Void>never())).subscribe();
        assertNotNull(firstContext.get());
        assertEquals("tenant-a", firstContext.get().tenantId());
        subscription.dispose();

        assertTrue(releaseSignal.await(3, TimeUnit.SECONDS));
        assertEquals(1, released.get());
        assertEquals(0, completed.get());
        AtomicReference<WebRequestContext> nextContext = new AtomicReference<>();
        MockServerWebExchange next = MockServerWebExchange.from(MockServerHttpRequest.get("/orders")
                .header(WebHeaders.TENANT_ID, "tenant-b"));
        filter.filter(next, ignored -> Ddd4jWebFluxContext.currentRequest()
                .doOnNext(nextContext::set).then()).block();
        assertNotNull(nextContext.get());
        assertEquals("tenant-b", nextContext.get().tenantId());
        assertNull(Ddd4jWebFluxContext.currentRequest().block());
        assertNull(Ddd4jWebFluxContext.currentSubject().block());
    }

    /**
     * \u9a8c\u8bc1<code>acquiring.await(3, TimeUnit.SECONDS)</code>\u4e3a\u771f\uff0c<code>acquired.await(3, TimeUnit.SECONDS)</code>\u4e3a\u771f\uff0c<code>released.await(3, TimeUnit.SECONDS)</code>\u4e3a\u771f\u3002
     * @throws InterruptedException \u7b49\u5f85\u6d4b\u8bd5\u64cd\u4f5c\u65f6\u88ab\u4e2d\u65ad
     */
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


    /** \u9a8c\u8bc1<code>captured.get()</code> \u4e0e\u2026\u4e3a\u540c\u4e00\u5b9e\u4f8b <code>subject</code>\uff0c<code>anonymousHasSubject.get()</code>\u4e3a\u5047\uff0c<code>Ddd4jWebFluxContext.currentSubject().block()</code>\u4e3a\u7a7a\u3002 */
    @Test
    void shouldClearAuthenticatedSubjectAfterCancellationAndRejectMissingBearer() {
        Subject subject = mock(Subject.class);
        when(subject.verify("valid-token")).thenReturn(new AuthPrincipal().setUserId("contract-user"));
        BaseContext.inject(SpiKeys.SUBJECT_PROVIDER, SubjectProvider.class, new SubjectProvider() {
            @Override
            public Subject getSubject() {
                return subject;
            }
        });
        try {
            Ddd4jWebFluxFilter requiredFilter = new Ddd4jWebFluxFilter(
                    new WebRequestContextFactory(),
                    new WebRequestLifecycle(new BearerSubjectAuthenticator(), WebAccessPolicy.required()),
                    null, Schedulers.immediate());
            AtomicReference<Subject> captured = new AtomicReference<>();
            MockServerWebExchange authenticated = MockServerWebExchange.from(MockServerHttpRequest.get("/orders")
                    .header(WebHeaders.TENANT_ID, "tenant-a")
                    .header(WebHeaders.AUTHORIZATION, "Bearer valid-token"));
            Disposable subscription = requiredFilter.filter(authenticated,
                    ignored -> Ddd4jWebFluxContext.currentSubject()
                            .doOnNext(captured::set).then(Mono.<Void>never())).subscribe();
            assertSame(subject, captured.get());
            subscription.dispose();

            Ddd4jWebFluxFilter optionalFilter = new Ddd4jWebFluxFilter(
                    new WebRequestContextFactory(),
                    new WebRequestLifecycle(new BearerSubjectAuthenticator(), WebAccessPolicy.optional()),
                    null, Schedulers.immediate());
            AtomicBoolean anonymousHasSubject = new AtomicBoolean(true);
            MockServerWebExchange anonymous = MockServerWebExchange.from(MockServerHttpRequest.get("/orders")
                    .header(WebHeaders.TENANT_ID, "tenant-b"));
            optionalFilter.filter(anonymous, ignored -> Ddd4jWebFluxContext.currentSubject()
                    .hasElement().doOnNext(anonymousHasSubject::set).then()).block();
            assertFalse(anonymousHasSubject.get());

            AtomicBoolean protectedChainInvoked = new AtomicBoolean();
            MockServerWebExchange missingBearer = MockServerWebExchange.from(
                    MockServerHttpRequest.get("/protected"));
            WebStatusException failure = assertThrows(WebStatusException.class,
                    () -> requiredFilter.filter(missingBearer, ignored -> {
                        protectedChainInvoked.set(true);
                        return Mono.empty();
                    }).block());
            assertEquals(401, failure.getStatus());
            assertFalse(protectedChainInvoked.get());
            assertNull(Ddd4jWebFluxContext.currentSubject().block());
        } finally {
            BaseContext.clear();
        }
    }

}
