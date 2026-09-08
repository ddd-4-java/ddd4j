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
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Ddd4jJavalinOtelScopeTest {

    @Test
    void requestCompletionMustCloseOtelScope() throws Exception {
        resetOpenTelemetry();
        SdkTracerProvider tracerProvider = SdkTracerProvider.builder().build();
        OpenTelemetrySdk.builder().setTracerProvider(tracerProvider).buildAndRegisterGlobal();
        AtomicBoolean activeDuringHandler = new AtomicBoolean();
        AtomicBoolean activeAfterDdd4jCompletion = new AtomicBoolean();
        Javalin app = Javalin.create(config -> config.showJavalinBanner = false);
        try {
            Ddd4jJavalinWeb web = new Ddd4jJavalinWeb(
                    new WebRequestContextFactory(),
                    new WebRequestLifecycle(new BearerSubjectAuthenticator(), WebAccessPolicy.disabled()),
                    new DefaultWebExceptionTranslator(), null);
            web.configure(app);
            app.get("/test", context -> {
                activeDuringHandler.set(Span.current().getSpanContext().isValid());
                context.result("ok");
            });
            app.after(context ->
                    activeAfterDdd4jCompletion.set(Span.current().getSpanContext().isValid()));
            app.start(0);

            HttpURLConnection connection = (HttpURLConnection) new URL(
                    "http://127.0.0.1:" + app.port() + "/test").openConnection();
            assertEquals(200, connection.getResponseCode());
            connection.disconnect();

            assertTrue(activeDuringHandler.get());
            assertFalse(activeAfterDdd4jCompletion.get());
        } finally {
            app.stop();
            tracerProvider.close();
            resetOpenTelemetry();
        }
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
