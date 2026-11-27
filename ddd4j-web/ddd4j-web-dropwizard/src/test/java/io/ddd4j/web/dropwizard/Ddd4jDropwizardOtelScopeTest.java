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
package io.ddd4j.web.dropwizard;

import io.ddd4j.cache.CacheKit;
import io.ddd4j.core.context.ThreadContext;
import io.ddd4j.extension.otel.Ddd4jOtel;
import io.ddd4j.web.core.auth.BearerSubjectAuthenticator;
import io.ddd4j.web.core.auth.WebAccessPolicy;
import io.ddd4j.web.core.context.WebContextScope;
import io.ddd4j.web.core.context.WebHeaders;
import io.ddd4j.web.core.context.WebRequestContextFactory;
import io.ddd4j.web.core.context.WebRequestLifecycle;
import io.ddd4j.web.core.idempotency.CacheIdempotencyGuard;
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
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.UriInfo;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * 通过 <code>CacheKit.build()</code>、<code>ThreadContext.put()</code>、<code>request()</code> 验证 <code>Ddd4jDropwizardOtelScope</code> 的运行行为。
 */
class Ddd4jDropwizardOtelScopeTest {

    /**
     * 调用 <code>mock()</code>、<code>request.getMethod()</code>、<code>request.getUriInfo()</code> 执行当前夹具操作。
     *
     * @return 根据测试条件返回的可能值，包括 <code>null</code>、<code>request</code>
     */
    private static ContainerRequestContext request() {
        ContainerRequestContext request = mock(ContainerRequestContext.class);
        UriInfo uriInfo = mock(UriInfo.class);
        Map<String, Object> properties = new HashMap<>();
        when(request.getMethod()).thenReturn("GET");
        when(request.getUriInfo()).thenReturn(uriInfo);
        when(uriInfo.getRequestUri()).thenReturn(URI.create("http://localhost/test"));
        when(request.getHeaders()).thenReturn(new MultivaluedHashMap<>());
        when(request.getHeaderString(anyString())).thenReturn(null);
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
     * 验证<code>ThreadContext.get(WebContextScope.REQUEST_ID)</code> 等于 <code>"inner"</code>，<code>responseFilter.filter(request, failedResponse)</code> 抛出 <code>IllegalStateException.class</code>，<code>ThreadContext.get(WebContextScope.REQUEST_ID)</code> 等于 <code>"outer"</code>。
     */
    @Test
    void headerWriteFailureRestoresOuterContextAndAllowsRetry() {
        String cacheName = "dropwizard-header-failure";
        CacheKit.build(cacheName, 300L);
        try {
            ThreadContext.put(WebContextScope.REQUEST_ID, "outer");
            ContainerRequestContext request = request();
            when(request.getHeaderString(WebHeaders.REQUEST_ID)).thenReturn("inner");
            when(request.getHeaderString(WebHeaders.IDEMPOTENCY_KEY)).thenReturn("retry-key");
            Ddd4jDropwizardRequestFilter requestFilter = new Ddd4jDropwizardRequestFilter(
                    new WebRequestContextFactory(),
                    new WebRequestLifecycle(new BearerSubjectAuthenticator(), WebAccessPolicy.disabled()),
                    new WebIdempotencyLifecycle(new CacheIdempotencyGuard(cacheName)));
            Ddd4jDropwizardResponseFilter responseFilter = new Ddd4jDropwizardResponseFilter();
            ContainerResponseContext failedResponse = response();
            IllegalStateException failure = new IllegalStateException("headers already committed");
            @SuppressWarnings("unchecked")
            MultivaluedMap<String, Object> headers = mock(MultivaluedMap.class);
            doThrow(failure).when(headers).putSingle(WebHeaders.REQUEST_ID, "inner");
            when(failedResponse.getHeaders()).thenReturn(headers);

            requestFilter.filter(request);
            assertEquals("inner", ThreadContext.get(WebContextScope.REQUEST_ID));
            assertSame(failure, assertThrows(IllegalStateException.class,
                    () -> responseFilter.filter(request, failedResponse)));
            assertEquals("outer", ThreadContext.get(WebContextScope.REQUEST_ID));
            requestFilter.filter(request);
            responseFilter.filter(request, response());
            assertEquals("outer", ThreadContext.get(WebContextScope.REQUEST_ID));
        } finally {
            ThreadContext.clear();
            CacheKit.unregister(cacheName);
        }
    }

    /**
     * 验证<code>observed[0]</code>为真，<code>observed[1]</code>为假。
     *
     * @throws Exception 测试服务器请求、会话操作或响应解析失败
     */
    @Test
    void responseFilterMustCloseRequestOtelScope() throws Exception {
        resetOpenTelemetry();
        SdkTracerProvider tracerProvider = SdkTracerProvider.builder()
                .addSpanProcessor(SimpleSpanProcessor.create(SpanExporter.composite())).build();
        OpenTelemetrySdk.builder().setTracerProvider(tracerProvider).buildAndRegisterGlobal();
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<boolean[]> result = executor.submit(() -> {
                ContainerRequestContext request = request();
                ContainerResponseContext response = response();
                Ddd4jDropwizardRequestFilter requestFilter = new Ddd4jDropwizardRequestFilter(
                        new WebRequestContextFactory(),
                        new WebRequestLifecycle(new BearerSubjectAuthenticator(), WebAccessPolicy.disabled()), null);
                Ddd4jDropwizardResponseFilter responseFilter = new Ddd4jDropwizardResponseFilter();

                requestFilter.filter(request);
                boolean activeDuringRequest = Span.current().getSpanContext().isValid();
                responseFilter.filter(request, response);
                return new boolean[]{activeDuringRequest, Span.current().getSpanContext().isValid()};
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
