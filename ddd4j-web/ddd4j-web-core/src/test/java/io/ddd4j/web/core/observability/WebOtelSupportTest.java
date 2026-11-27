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
package io.ddd4j.web.core.observability;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import io.ddd4j.extension.otel.Ddd4jOtel;
import io.ddd4j.extension.otel.HttpSpan;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 类型说明。
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 3.0.x
 */
class WebOtelSupportTest {

    /** \u7528\u4e8e\u6355\u83b7\u6d4b\u8bd5\u7ed3\u675f\u524d\u5df2\u5bfc\u51fa\u7684\u8ddf\u8e2a\u6570\u636e\u3002 */
    private InMemorySpanExporter exporter;
    /** \u4e3a\u6d4b\u8bd5\u6536\u96c6 OpenTelemetry \u8ddf\u8e2a\u4fe1\u606f\u7684\u63d0\u4f9b\u5668\u3002 */
    private SdkTracerProvider tracerProvider;

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
     * \u6d4b\u8bd5\u6267\u884c\u524d\u6267\u884c <code>resetOpenTelemetry()</code>\u3001<code>InMemorySpanExporter.create()</code>\u3001<code>SdkTracerProvider.builder()</code>\u3001<code>SimpleSpanProcessor.create()</code> \u5b8c\u6210\u8d44\u6e90\u51c6\u5907\u3002
     * @throws Exception \u5f53\u524d\u7528\u4f8b\u51c6\u5907\u8d44\u6e90\u6216\u8c03\u7528\u6d4b\u8bd5\u5165\u53e3\u5931\u8d25
     */
    @BeforeEach
    void setUp() throws Exception {
        resetOpenTelemetry();
        exporter = InMemorySpanExporter.create();
        tracerProvider = SdkTracerProvider.builder()
                .addSpanProcessor(SimpleSpanProcessor.create(exporter))
                .build();
        OpenTelemetrySdk.builder()
                .setTracerProvider(tracerProvider)
                .setPropagators(ContextPropagators.create(W3CTraceContextPropagator.getInstance()))
                .buildAndRegisterGlobal();
    }

    /**
     * \u6d4b\u8bd5\u7ed3\u675f\u540e\u6267\u884c <code>tracerProvider.close()</code>\u3001<code>exporter.close()</code>\u3001<code>resetOpenTelemetry()</code> \u5b8c\u6210\u8d44\u6e90\u56de\u6536\u6216\u72b6\u6001\u6062\u590d\u3002
     * @throws Exception \u5f53\u524d\u7528\u4f8b\u51c6\u5907\u8d44\u6e90\u6216\u8c03\u7528\u6d4b\u8bd5\u5165\u53e3\u5931\u8d25
     */
    @AfterEach
    void tearDown() throws Exception {
        if (tracerProvider != null) {
            tracerProvider.close();
        }
        if (exporter != null) {
            exporter.close();
        }
        resetOpenTelemetry();
    }

    /**
     * \u9a8c\u8bc1<code>WebOtelSupport.isAvailable()</code>\u4e3a\u771f\uff0c<code>spanObject instanceof Span</code>\u4e3a\u771f\u3002
     * @throws Exception \u5f53\u524d\u7528\u4f8b\u51c6\u5907\u8d44\u6e90\u6216\u8c03\u7528\u6d4b\u8bd5\u5165\u53e3\u5931\u8d25
     */
    @Test
    void startAndActivateDelegateToRealOtelIntegration() throws Exception {
        assertTrue(WebOtelSupport.isAvailable());
        Object spanObject = WebOtelSupport.startServerSpan("GET", "/api", new HashMap<>());
        assertTrue(spanObject instanceof Span);
        Span span = (Span) spanObject;
        assertTrue(span.getSpanContext().isValid());

        try (AutoCloseable scope = WebOtelSupport.activate(span)) {
            assertSame(span, Span.current());
        }

        assertFalse(Span.current().getSpanContext().isValid());
        WebOtelSupport.endServerSpan(span, 200);
    }

    /** \u9a8c\u8bc1<code>spans.size()</code> \u7b49\u4e8e <code>1</code>\uff0c<code>data.getStatus().getStatusCode()</code> \u7b49\u4e8e <code>StatusCode.ERROR</code>\uff0c<code>data.getEvents().isEmpty()</code>\u4e3a\u5047\u3002 */
    @Test
    void errorAndStatusAreExportedThroughReflectionBridge() {
        Object span = WebOtelSupport.startServerSpan("POST", "/orders", new HashMap<>());

        WebOtelSupport.recordError(span, new IllegalStateException("failed"));
        WebOtelSupport.endServerSpan(span, 503);

        List<SpanData> spans = exporter.getFinishedSpanItems();
        assertEquals(1, spans.size());
        SpanData data = spans.get(0);
        assertEquals(StatusCode.ERROR, data.getStatus().getStatusCode());
        assertEquals("503", data.getAttributes().get(HttpSpan.ATTR_HTTP_STATUS));
        assertFalse(data.getEvents().isEmpty());
    }

    /**
     * \u9a8c\u8bc1<code>headers.get("traceparent")</code>\u975e\u7a7a\uff0c<code>headers.get("traceparent").startsWith("00-")</code>\u4e3a\u771f\u3002
     * @throws Exception \u5f53\u524d\u7528\u4f8b\u51c6\u5907\u8d44\u6e90\u6216\u8c03\u7528\u6d4b\u8bd5\u5165\u53e3\u5931\u8d25
     */
    @Test
    void responseContextIsInjectedThroughReflectionBridge() throws Exception {
        Object span = WebOtelSupport.startServerSpan("GET", "/api", new HashMap<>());
        Map<String, String> headers = new HashMap<>();

        try (AutoCloseable scope = WebOtelSupport.activate(span)) {
            WebOtelSupport.injectResponseContext(headers);
        }
        WebOtelSupport.endServerSpan(span, 200);

        assertNotNull(headers.get("traceparent"));
        assertTrue(headers.get("traceparent").startsWith("00-"));
    }

    /** \u9a8c\u8bc1<code>WebOtelSupport.startServerSpan("GET", "/api", null)</code>\u975e\u7a7a\u3002 */
    @Test
    void nullInputsRemainSafe() {
        assertNotNull(WebOtelSupport.startServerSpan("GET", "/api", null));
        assertDoesNotThrow(() -> WebOtelSupport.activate(null).close());
        assertDoesNotThrow(() -> WebOtelSupport.recordError(null, null));
        assertDoesNotThrow(() -> WebOtelSupport.endServerSpan(null, 500));
        assertDoesNotThrow(() -> WebOtelSupport.injectResponseContext(null));
    }
}
