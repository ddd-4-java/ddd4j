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
package io.ddd4j.web.javalin;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;

import io.ddd4j.extension.otel.Ddd4jOtel;
import io.ddd4j.web.core.auth.BearerSubjectAuthenticator;
import io.ddd4j.web.core.auth.WebAccessPolicy;
import io.ddd4j.web.core.context.WebRequestContextFactory;
import io.ddd4j.web.core.context.WebRequestLifecycle;
import io.ddd4j.web.core.error.DefaultWebExceptionTranslator;
import io.javalin.Javalin;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 类型说明。
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 3.0.x
 */
class Ddd4jJavalinOtelScopeTest {

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
     * \u9a8c\u8bc1<code>connection.getResponseCode()</code> \u7b49\u4e8e <code>200</code>\uff0c<code>activeAfterDdd4jCompletion.get()</code>\u4e3a\u5047\u3002
     * @throws Exception \u51c6\u5907\u6216\u91ca\u653e\u88ab\u6d4b\u8fd0\u884c\u8d44\u6e90\u65f6\u5931\u8d25
     */
    @Test
    void requestCompletionMustCloseOtelScope() throws Exception {
        resetOpenTelemetry();
        SdkTracerProvider tracerProvider = SdkTracerProvider.builder()
                .addSpanProcessor(SimpleSpanProcessor.create(InMemorySpanExporter.create()))
                .build();
        OpenTelemetrySdk.builder().setTracerProvider(tracerProvider).buildAndRegisterGlobal();
        AtomicBoolean activeDuringHandler = new AtomicBoolean();
        AtomicBoolean activeAfterDdd4jCompletion = new AtomicBoolean();
        Javalin app = null;
        try {
            Ddd4jJavalinWeb web = new Ddd4jJavalinWeb(
                    new WebRequestContextFactory(),
                    new WebRequestLifecycle(new BearerSubjectAuthenticator(), WebAccessPolicy.disabled()),
                    new DefaultWebExceptionTranslator(), null);
            app = Javalin.create(config -> {
                config.startup.showJavalinBanner = false;
                web.configure(config);
                config.routes.get("/test", context -> {
                    activeDuringHandler.set(Span.current().getSpanContext().isValid());
                    context.result("ok");
                });
                config.routes.after(context ->
                        activeAfterDdd4jCompletion.set(Span.current().getSpanContext().isValid()));
            }).start(0);

            HttpURLConnection connection = (HttpURLConnection) new URL(
                    "http://127.0.0.1:" + app.port() + "/test").openConnection();
            assertEquals(200, connection.getResponseCode());
            connection.disconnect();

            assertTrue(activeDuringHandler.get());
            assertFalse(activeAfterDdd4jCompletion.get());
        } finally {
            if (app != null) {
                app.stop();
            }
            tracerProvider.close();
            resetOpenTelemetry();
        }
    }
}
