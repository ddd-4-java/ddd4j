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
import org.junit.jupiter.api.Test;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;

import java.lang.reflect.Field;
import java.net.InetSocketAddress;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class Ddd4jMicronautOtelScopeTest {

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
}
