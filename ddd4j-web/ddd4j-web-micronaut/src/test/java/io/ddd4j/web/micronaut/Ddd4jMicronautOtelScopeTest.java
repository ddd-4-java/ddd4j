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
package io.ddd4j.web.micronaut;

import io.ddd4j.extension.otel.Ddd4jOtel;
import io.ddd4j.web.core.auth.AuthenticationMode;
import io.ddd4j.web.core.auth.BearerSubjectAuthenticator;
import io.ddd4j.web.core.auth.PathWebAccessPolicy;
import io.ddd4j.web.core.auth.WebAccessPolicy;
import io.ddd4j.web.core.context.ClientIpResolver;
import io.ddd4j.web.core.context.WebRequestContextFactory;
import io.ddd4j.web.core.context.WebRequestLifecycle;
import io.micronaut.core.propagation.MutablePropagatedContext;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.filter.FilterContinuation;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import org.junit.jupiter.api.Test;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;

import java.lang.reflect.Field;
import java.net.InetSocketAddress;
import java.util.Collections;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 通过 <code>resetOpenTelemetry()</code>、<code>InMemorySpanExporter.create()</code>、<code>SdkTracerProvider.builder()</code> 验证 <code>Ddd4jMicronautOtelScope</code> 的运行行为。
 */
class Ddd4jMicronautOtelScopeTest {

    /**
     * 读取 <code>request</code> 供当前测试使用。
     *
     * @return 当前测试使用的结果，取自 <code>request</code>
     */
    private static HttpRequest<?> request() {
        HttpRequest<?> request = mock(HttpRequest.class);
        io.micronaut.http.HttpHeaders headers = mock(io.micronaut.http.HttpHeaders.class);
        when(request.getMethodName()).thenReturn("GET");
        when(request.getPath()).thenReturn("/test");
        when(request.getHeaders()).thenReturn(headers);
        when(request.getLocale()).thenReturn(Optional.of(Locale.getDefault()));
        when(request.getRemoteAddress()).thenReturn(new InetSocketAddress("127.0.0.1", 8080));
        return request;
    }

    /**
     * 调用 <code>GlobalOpenTelemetry.resetForTest()</code>、<code>Ddd4jOtel.class.getDeclaredField()</code>、<code>field.setAccessible()</code> 执行当前夹具操作。
     *
     * @throws Exception 反射访问被测成员时失败
     */
    private static void resetOpenTelemetry() throws Exception {
        GlobalOpenTelemetry.resetForTest();
        for (String fieldName : new String[]{"TRACER_CACHE", "METER_CACHE"}) {
            Field field = Ddd4jOtel.class.getDeclaredField(fieldName);
            field.setAccessible(true);
            ((AtomicReference<?>) field.get(null)).set(null);
        }
        Field available = Ddd4jOtel.class.getDeclaredField("available");
        available.setAccessible(true);
        available.setBoolean(null, false);
    }

    /**
     * 验证<code>filter.filter(request(), continuation, propagatedContext)</code> 抛出 <code>IllegalStateException.class</code>，<code>exporter.getFinishedSpanItems().size()</code> 等于 <code>1</code>。
     *
     * @throws Exception 测试服务器请求、会话操作或响应解析失败
     */
    @Test
    void synchronousDownstreamFailureEndsServerSpan() throws Exception {
        resetOpenTelemetry();
        InMemorySpanExporter exporter = InMemorySpanExporter.create();
        SdkTracerProvider tracerProvider = SdkTracerProvider.builder()
                .addSpanProcessor(SimpleSpanProcessor.create(exporter)).build();
        OpenTelemetrySdk.builder().setTracerProvider(tracerProvider).buildAndRegisterGlobal();
        try {
            Ddd4jMicronautWebFilter filter = new Ddd4jMicronautWebFilter(
                    new WebRequestContextFactory(() -> "request-1", ClientIpResolver.remoteAddressOnly()),
                    new WebRequestLifecycle(new BearerSubjectAuthenticator(), WebAccessPolicy.disabled()), null);
            @SuppressWarnings("unchecked")
            FilterContinuation<Publisher<MutableHttpResponse<?>>> continuation =
                    (FilterContinuation<Publisher<MutableHttpResponse<?>>>) mock(FilterContinuation.class);
            when(continuation.proceed()).thenThrow(new IllegalStateException("downstream failed"));
            MutablePropagatedContext propagatedContext = mock(MutablePropagatedContext.class);

            assertThrows(IllegalStateException.class,
                    () -> filter.filter(request(), continuation, propagatedContext));
            assertEquals(1, exporter.getFinishedSpanItems().size());
        } finally {
            tracerProvider.close();
            exporter.close();
            resetOpenTelemetry();
        }
    }

    /**
     * 验证<code>filter.filter(request(), continuation, propagatedContext)</code> 抛出 <code>io.ddd4j.web.core.error.WebStatusException.class</code>，<code>exporter.getFinishedSpanItems().size()</code> 等于 <code>1</code>。
     *
     * @throws Exception 测试服务器请求、会话操作或响应解析失败
     */
    @Test
    void authenticationFailureEndsServerSpan() throws Exception {
        resetOpenTelemetry();
        InMemorySpanExporter exporter = InMemorySpanExporter.create();
        SdkTracerProvider tracerProvider = SdkTracerProvider.builder()
                .addSpanProcessor(SimpleSpanProcessor.create(exporter)).build();
        OpenTelemetrySdk.builder().setTracerProvider(tracerProvider).buildAndRegisterGlobal();
        try {
            Ddd4jMicronautWebFilter filter = new Ddd4jMicronautWebFilter(
                    new WebRequestContextFactory(() -> "request-1", ClientIpResolver.remoteAddressOnly()),
                    new WebRequestLifecycle(new BearerSubjectAuthenticator(),
                            new PathWebAccessPolicy(Collections.emptyList(), AuthenticationMode.REQUIRED)), null);
            @SuppressWarnings("unchecked")
            FilterContinuation<Publisher<MutableHttpResponse<?>>> continuation =
                    (FilterContinuation<Publisher<MutableHttpResponse<?>>>) mock(FilterContinuation.class);
            MutablePropagatedContext propagatedContext = mock(MutablePropagatedContext.class);

            assertThrows(io.ddd4j.web.core.error.WebStatusException.class,
                    () -> filter.filter(request(), continuation, propagatedContext));
            assertEquals(1, exporter.getFinishedSpanItems().size());
        } finally {
            tracerProvider.close();
            exporter.close();
            resetOpenTelemetry();
        }
    }

    /**
     * 验证<code>observed[0]</code>为真，<code>observed[2]</code>为假。
     *
     * @throws Exception 测试服务器请求、会话操作或响应解析失败
     */
    @Test
    void filterMustCloseOtelScopeOnCallingThread() throws Exception {
        resetOpenTelemetry();
        SdkTracerProvider tracerProvider = SdkTracerProvider.builder()
                .addSpanProcessor(SimpleSpanProcessor.create(SpanExporter.composite())).build();
        OpenTelemetrySdk.builder().setTracerProvider(tracerProvider).buildAndRegisterGlobal();
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<boolean[]> result = executor.submit(() -> {
                Ddd4jMicronautWebFilter filter = new Ddd4jMicronautWebFilter(
                        new WebRequestContextFactory(() -> "request-1", ClientIpResolver.remoteAddressOnly()),
                        new WebRequestLifecycle(new BearerSubjectAuthenticator(), WebAccessPolicy.disabled()), null);
                HttpRequest<?> request = request();
                @SuppressWarnings("unchecked")
                FilterContinuation<Publisher<MutableHttpResponse<?>>> continuation =
                        (FilterContinuation<Publisher<MutableHttpResponse<?>>>) mock(FilterContinuation.class);
                MutableHttpResponse<?> response = mock(MutableHttpResponse.class);
                when(response.getStatus()).thenReturn(HttpStatus.OK);
                AtomicBoolean chainObservedSpan = new AtomicBoolean();
                when(continuation.proceed()).thenAnswer(invocation -> {
                    chainObservedSpan.set(Span.current().getSpanContext().isValid());
                    return Flux.just(response);
                });
                MutablePropagatedContext propagatedContext = mock(MutablePropagatedContext.class);

                Publisher<MutableHttpResponse<?>> publisher =
                        filter.filter(request, continuation, propagatedContext);
                boolean leakedAfterFilterReturned = Span.current().getSpanContext().isValid();
                Flux.from(publisher).blockLast();
                return new boolean[]{chainObservedSpan.get(), leakedAfterFilterReturned,
                        Span.current().getSpanContext().isValid()};
            });
            boolean[] observed = result.get();

            assertTrue(observed[0]);
            assertFalse(observed[1]);
            assertFalse(observed[2]);
        } finally {
            executor.shutdownNow();
            tracerProvider.close();
            resetOpenTelemetry();
        }
    }
}
