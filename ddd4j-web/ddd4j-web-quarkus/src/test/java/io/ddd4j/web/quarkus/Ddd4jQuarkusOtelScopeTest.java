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

/**
 * 通过 <code>resetOpenTelemetry()</code>、<code>SdkTracerProvider.builder()</code>、<code>SimpleSpanProcessor.create()</code> 验证 <code>Ddd4jQuarkusOtelScope</code> 的运行行为。
 */
class Ddd4jQuarkusOtelScopeTest {

    /**
     * 调用 <code>mock()</code>、<code>headers.put()</code>、<code>request.getMethod()</code> 执行当前夹具操作。
     *
     * @return 根据测试条件返回的可能值，包括 <code>null</code>、<code>request</code>
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
     * 读取 <code>response</code> 供当前测试使用。
     *
     * @return 当前测试使用的结果，取自 <code>response</code>
     */
    private static ContainerResponseContext response() {
        ContainerResponseContext response = mock(ContainerResponseContext.class);
        when(response.getStatus()).thenReturn(200);
        when(response.getHeaders()).thenReturn(new MultivaluedHashMap<>());
        return response;
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
     * 验证<code>observed[0]</code>为真，<code>observed[1]</code>为假。
     *
     * @throws Exception 发起测试请求时失败
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
     * 验证<code>observed[0]</code>为真，<code>observed[1]</code>为假。
     *
     * @throws Exception 发起测试请求时失败
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
