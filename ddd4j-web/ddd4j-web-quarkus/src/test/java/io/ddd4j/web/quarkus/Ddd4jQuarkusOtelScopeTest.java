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
package io.ddd4j.web.quarkus;

import io.ddd4j.extension.otel.Ddd4jOtel;
import io.ddd4j.web.core.auth.BearerSubjectAuthenticator;
import io.ddd4j.web.core.auth.WebAccessPolicy;
import io.ddd4j.web.core.context.WebHeaders;
import io.ddd4j.web.core.context.WebRequestContextFactory;
import io.ddd4j.web.core.context.WebRequestLifecycle;
import io.ddd4j.web.core.idempotency.IdempotencyGuard;
import io.ddd4j.web.core.idempotency.WebIdempotencyLifecycle;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.core.MultivaluedHashMap;
import jakarta.ws.rs.core.UriInfo;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.net.URI;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/** \u901a\u8fc7 <code>resetOpenTelemetry()</code>\u3001<code>SdkTracerProvider.builder()</code>\u3001<code>SimpleSpanProcessor.create()</code> \u9a8c\u8bc1 <code>Ddd4jQuarkusOtelScope</code> \u7684\u8fd0\u884c\u884c\u4e3a\u3002 */
class Ddd4jQuarkusOtelScopeTest {

    /**
     * \u8c03\u7528 <code>mock()</code>\u3001<code>headers.put()</code>\u3001<code>request.getMethod()</code> \u6267\u884c\u5f53\u524d\u5939\u5177\u64cd\u4f5c\u3002
     * @return \u6839\u636e\u6d4b\u8bd5\u6761\u4ef6\u8fd4\u56de\u7684\u53ef\u80fd\u503c\uff0c\u5305\u62ec <code>null</code>\u3001<code>request</code>
     */
    private static ContainerRequestContext request() {
        ContainerRequestContext request = mock(ContainerRequestContext.class);
        UriInfo uriInfo = mock(UriInfo.class);
        Map<String, Object> properties = new HashMap<>();
        Map<String, String> headers = new HashMap<>();
        headers.put(WebHeaders.IDEMPOTENCY_KEY, "order-1");
        when(request.getMethod()).thenReturn("POST");
        when(request.getUriInfo()).thenReturn(uriInfo);
        when(uriInfo.getRequestUri()).thenReturn(URI.create("http://localhost/orders"));
        when(request.getHeaders()).thenReturn(new MultivaluedHashMap<>());
        when(request.getHeaderString(anyString())).thenAnswer(invocation ->
                headers.get(invocation.getArgument(0)));
        when(request.getProperty(anyString())).thenAnswer(invocation ->
                properties.get(invocation.getArgument(0)));
        doAnswer(invocation -> {
            properties.put(invocation.getArgument(0), invocation.getArgument(1));
            return null;
        }).when(request).setProperty(anyString(), org.mockito.ArgumentMatchers.any());
        doAnswer(invocation -> {
            properties.remove(invocation.getArgument(0));
            return null;
        }).when(request).removeProperty(anyString());
        return request;
    }

    /**
     * \u8bfb\u53d6 <code>response</code> \u4f9b\u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u3002
     * @return \u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u7ed3\u679c\uff0c\u53d6\u81ea <code>response</code>
     */
    private static ContainerResponseContext response() {
        ContainerResponseContext response = mock(ContainerResponseContext.class);
        when(response.getStatus()).thenReturn(200);
        when(response.getHeaders()).thenReturn(new MultivaluedHashMap<>());
        return response;
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
     * \u9a8c\u8bc1<code>observed[0]</code>\u4e3a\u771f\uff0c<code>observed[1]</code>\u4e3a\u5047\u3002
     * @throws Exception \u53d1\u8d77\u6d4b\u8bd5\u8bf7\u6c42\u65f6\u5931\u8d25
     */
    @Test
    void idempotencyCompletionFailureMustStillCloseRequestScopes() throws Exception {
        resetOpenTelemetry();
        SdkTracerProvider tracerProvider = SdkTracerProvider.builder()
                .addSpanProcessor(SimpleSpanProcessor.create(SpanExporter.composite())).build();
        OpenTelemetrySdk.builder().setTracerProvider(tracerProvider).buildAndRegisterGlobal();
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<boolean[]> result = executor.submit(() -> {
                IdempotencyGuard guard = new IdempotencyGuard() {
                    @Override
                    public boolean acquire(String key, Duration ttl) {
                        return true;
                    }

                    @Override
                    public void complete(String key) {
                        throw new IllegalStateException("complete failed");
                    }

                    @Override
                    public void release(String key) {
                    }
                };
                Ddd4jQuarkusWebFilter filter = new Ddd4jQuarkusWebFilter(
                        new WebRequestContextFactory(),
                        new WebRequestLifecycle(new BearerSubjectAuthenticator(), WebAccessPolicy.disabled()),
                        new WebIdempotencyLifecycle(guard));
                ContainerRequestContext request = request();
                filter.request(request);
                boolean failed = false;
                try {
                    filter.response(request, response());
                } catch (IllegalStateException expected) {
                    failed = true;
                }
                return new boolean[]{failed, Span.current().getSpanContext().isValid()};
            });
            boolean[] observed = result.get();

            assertTrue(observed[0]);
            assertFalse(observed[1]);
        } finally {
            executor.shutdownNow();
            tracerProvider.close();
            resetOpenTelemetry();
        }
    }

    /**
     * \u9a8c\u8bc1<code>observed[0]</code>\u4e3a\u771f\uff0c<code>observed[1]</code>\u4e3a\u5047\u3002
     * @throws Exception \u53d1\u8d77\u6d4b\u8bd5\u8bf7\u6c42\u65f6\u5931\u8d25
     */
    @Test
    void authenticationFailureMustCloseRequestOtelScopeImmediately() throws Exception {
        resetOpenTelemetry();
        SdkTracerProvider tracerProvider = SdkTracerProvider.builder()
                .addSpanProcessor(SimpleSpanProcessor.create(SpanExporter.composite())).build();
        OpenTelemetrySdk.builder().setTracerProvider(tracerProvider).buildAndRegisterGlobal();
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<boolean[]> result = executor.submit(() -> {
                Ddd4jQuarkusWebFilter filter = new Ddd4jQuarkusWebFilter(
                        new WebRequestContextFactory(),
                        new WebRequestLifecycle(new BearerSubjectAuthenticator(), WebAccessPolicy.required()), null);
                boolean failed = false;
                try {
                    filter.request(request());
                } catch (RuntimeException expected) {
                    failed = true;
                }
                return new boolean[]{failed, Span.current().getSpanContext().isValid()};
            });
            boolean[] observed = result.get();

            assertTrue(observed[0]);
            assertFalse(observed[1]);
        } finally {
            executor.shutdownNow();
            tracerProvider.close();
            resetOpenTelemetry();
        }
    }
}
