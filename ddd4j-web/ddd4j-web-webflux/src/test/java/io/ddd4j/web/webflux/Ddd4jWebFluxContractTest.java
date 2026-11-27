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

/**
 * 验证 <code>Ddd4jWebFluxContract</code> 在 <code>shouldWriteCustomHttpStatus</code>、<code>shouldReleaseIdempotencyLease</code> 等场景中的行为。
 */
class Ddd4jWebFluxContractTest {

    /**
     * 观察当前请求结束时关闭的 OpenTelemetry Span。
     */
    private final CompletableFuture<SpanData> endedSpan = new CompletableFuture<>();
    /**
     * 为测试收集 OpenTelemetry 跟踪信息的提供器。
     */
    private SdkTracerProvider tracerProvider;

    /**
     * 调用 <code>GlobalOpenTelemetry.resetForTest()</code>、<code>Class.forName()</code>、<code>ddd4jOtel.getDeclaredField()</code> 执行当前夹具操作。
     *
     * @throws Exception 反射访问被测成员时失败
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
     * 测试执行前执行 <code>resetOpenTelemetry()</code>、<code>SdkTracerProvider.builder()</code>、<code>OpenTelemetrySdk.builder()</code>、<code>ContextPropagators.create()</code> 完成资源准备。
     *
     * @throws Exception 当前用例准备资源或调用测试入口失败
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
     * 测试结束后执行 <code>tracerProvider.close()</code>、<code>resetOpenTelemetry()</code> 完成资源回收或状态恢复。
     *
     * @throws Exception 当前用例准备资源或调用测试入口失败
     */
    @AfterEach
    void tearDownOpenTelemetry() throws Exception {
        if (tracerProvider != null) {
            tracerProvider.close();
        }
        resetOpenTelemetry();
    }

    /**
     * 验证<code>requestContext</code>非空，<code>requestContext.tenantId()</code> 等于 <code>"tenant-a"</code>，<code>exchange.getResponse().getHeaders().getFirst(WebHeaders.REQUEST_ID)</code> 等于 <code>requestContext.requestId()</code>。
     */
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

    /**
     * 验证<code>exchange.getResponse().getStatusCode()</code> 等于 <code>HttpStatus.BAD_REQUEST</code>，<code>body</code>非空，<code>body.contains("400")</code>为真。
     */
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
     * 验证<code>exchange.getResponse().getStatusCode().value()</code> 等于 <code>599</code>，<code>body</code>非空，<code>span.getStatus().getStatusCode()</code> 等于 <code>StatusCode.ERROR</code>。
     *
     * @throws Exception 等待并发操作时失败
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
     * 验证<code>headers.get("traceparent")</code> 等于 <code>"00-0af7651916cd43dd8448eb211c80319c-b7ad6b7169203331-01"</code>，<code>headers.get("X-Multi")</code> 等于 <code>"first"</code>。
     *
     * @throws Exception 反射访问被测成员时失败
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

    /**
     * 验证<code>Ddd4jOtel.isAvailable()</code>为真，<code>assemblyThreadLeaked</code>为假，<code>Span.current().getSpanContext().isValid()</code>为假。
     */
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
     * 验证<code>firstContext.get()</code>非空，<code>firstContext.get().tenantId()</code> 等于 <code>"tenant-a"</code>，<code>Ddd4jWebFluxContext.currentSubject().block()</code>为空。
     *
     * @throws InterruptedException 等待测试操作时被中断
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
     * 验证<code>acquiring.await(3, TimeUnit.SECONDS)</code>为真，<code>acquired.await(3, TimeUnit.SECONDS)</code>为真，<code>released.await(3, TimeUnit.SECONDS)</code>为真。
     *
     * @throws InterruptedException 等待测试操作时被中断
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


    /**
     * 验证<code>captured.get()</code> 与…为同一实例 <code>subject</code>，<code>anonymousHasSubject.get()</code>为假，<code>Ddd4jWebFluxContext.currentSubject().block()</code>为空。
     */
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
