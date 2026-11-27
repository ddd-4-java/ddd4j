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
import io.ddd4j.web.core.auth.BearerSubjectAuthenticator;
import io.ddd4j.web.core.auth.AuthenticationMode;
import io.ddd4j.web.core.auth.PathWebAccessPolicy;
import io.ddd4j.web.core.auth.WebAccessPolicy;
import io.ddd4j.web.core.context.ClientIpResolver;
import io.ddd4j.web.core.context.WebRequestContextFactory;
import io.ddd4j.web.core.context.WebRequestLifecycle;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.filter.FilterChain;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import org.junit.jupiter.api.Test;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;

import java.lang.reflect.Field;
import java.net.InetSocketAddress;
import java.util.Locale;
import java.util.Optional;
import java.util.Collections;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** \u901a\u8fc7 <code>resetOpenTelemetry()</code>\u3001<code>InMemorySpanExporter.create()</code>\u3001<code>SdkTracerProvider.builder()</code> \u9a8c\u8bc1 <code>Ddd4jMicronautOtelScope</code> \u7684\u8fd0\u884c\u884c\u4e3a\u3002 */
class Ddd4jMicronautOtelScopeTest {

    /**
     * \u8bfb\u53d6 <code>request</code> \u4f9b\u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u3002
     * @return \u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u7ed3\u679c\uff0c\u53d6\u81ea <code>request</code>
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
     * \u8c03\u7528 <code>GlobalOpenTelemetry.resetForTest()</code>\u3001<code>Ddd4jOtel.class.getDeclaredField()</code>\u3001<code>field.setAccessible()</code> \u6267\u884c\u5f53\u524d\u5939\u5177\u64cd\u4f5c\u3002
     * @throws Exception \u53cd\u5c04\u8bbf\u95ee\u88ab\u6d4b\u6210\u5458\u65f6\u5931\u8d25
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
     * \u9a8c\u8bc1<code>observed[0]</code>\u4e3a\u771f\uff0c<code>observed[2]</code>\u4e3a\u5047\u3002
     * @throws Exception \u6d4b\u8bd5\u670d\u52a1\u5668\u8bf7\u6c42\u3001\u4f1a\u8bdd\u64cd\u4f5c\u6216\u54cd\u5e94\u89e3\u6790\u5931\u8d25
     */
    @Test
    void filterMustCloseOtelScopeOnCallingThread() throws Exception {
        resetOpenTelemetry();
        SdkTracerProvider tracerProvider = SdkTracerProvider.builder().build();
        OpenTelemetrySdk.builder().setTracerProvider(tracerProvider).buildAndRegisterGlobal();
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<boolean[]> result = executor.submit(() -> {
                Ddd4jMicronautWebFilter filter = new Ddd4jMicronautWebFilter(
                        new WebRequestContextFactory(() -> "request-1", ClientIpResolver.remoteAddressOnly()),
                        new WebRequestLifecycle(new BearerSubjectAuthenticator(), WebAccessPolicy.disabled()), null);
                HttpRequest<?> request = request();
                FilterChain chain = mock(FilterChain.class);
                AtomicBoolean chainObservedSpan = new AtomicBoolean();
                when(chain.proceed(any())).thenAnswer(invocation -> {
                    chainObservedSpan.set(Span.current().getSpanContext().isValid());
                    return Flux.just(HttpResponse.ok());
                });

                Publisher<? extends HttpResponse<?>> publisher = filter.doFilter(request, chain);
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

    /**
     * \u9a8c\u8bc1<code>filter.filter(request(), continuation, propagatedContext)</code> \u629b\u51fa <code>io.ddd4j.web.core.error.WebStatusException.class</code>\uff0c<code>exporter.getFinishedSpanItems().size()</code> \u7b49\u4e8e <code>1</code>\u3002
     * @throws Exception \u6d4b\u8bd5\u670d\u52a1\u5668\u8bf7\u6c42\u3001\u4f1a\u8bdd\u64cd\u4f5c\u6216\u54cd\u5e94\u89e3\u6790\u5931\u8d25
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
            FilterChain chain = mock(FilterChain.class);

            assertThrows(io.ddd4j.web.core.error.WebStatusException.class,
                    () -> filter.doFilter(request(), chain));
            assertEquals(1, exporter.getFinishedSpanItems().size());
        } finally {
            tracerProvider.close();
            exporter.close();
            resetOpenTelemetry();
        }
    }

    /**
     * \u9a8c\u8bc1<code>filter.filter(request(), continuation, propagatedContext)</code> \u629b\u51fa <code>IllegalStateException.class</code>\uff0c<code>exporter.getFinishedSpanItems().size()</code> \u7b49\u4e8e <code>1</code>\u3002
     * @throws Exception \u6d4b\u8bd5\u670d\u52a1\u5668\u8bf7\u6c42\u3001\u4f1a\u8bdd\u64cd\u4f5c\u6216\u54cd\u5e94\u89e3\u6790\u5931\u8d25
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
            FilterChain chain = mock(FilterChain.class);
            when(chain.proceed(any())).thenThrow(new IllegalStateException("downstream failed"));

            assertThrows(IllegalStateException.class, () -> filter.doFilter(request(), chain));
            assertEquals(1, exporter.getFinishedSpanItems().size());
        } finally {
            tracerProvider.close();
            exporter.close();
            resetOpenTelemetry();
        }
    }
}
