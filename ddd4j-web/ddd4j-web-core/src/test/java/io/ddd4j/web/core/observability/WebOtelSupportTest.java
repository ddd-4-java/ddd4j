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

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WebOtelSupportTest {

    private InMemorySpanExporter exporter;
    private SdkTracerProvider tracerProvider;

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

    @Test
    void nullInputsRemainSafe() {
        assertNotNull(WebOtelSupport.startServerSpan("GET", "/api", null));
        assertDoesNotThrow(() -> WebOtelSupport.activate(null).close());
        assertDoesNotThrow(() -> WebOtelSupport.recordError(null, null));
        assertDoesNotThrow(() -> WebOtelSupport.endServerSpan(null, 500));
        assertDoesNotThrow(() -> WebOtelSupport.injectResponseContext(null));
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
