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
package io.ddd4j.web.vertx;

import io.ddd4j.extension.otel.Ddd4jOtel;
import io.ddd4j.web.core.auth.BearerSubjectAuthenticator;
import io.ddd4j.web.core.auth.WebAccessPolicy;
import io.ddd4j.web.core.context.WebRequestContextFactory;
import io.ddd4j.web.core.context.WebRequestLifecycle;
import io.ddd4j.web.core.error.DefaultWebExceptionTranslator;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServer;
import io.vertx.ext.web.Router;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Ddd4jVertxOtelScopeTest {

    @Test
    void responseEndMustCloseOtelScopeOnEventLoop() throws Exception {
        resetOpenTelemetry();
        SdkTracerProvider tracerProvider = SdkTracerProvider.builder().build();
        OpenTelemetrySdk.builder().setTracerProvider(tracerProvider).buildAndRegisterGlobal();
        Vertx vertx = Vertx.vertx();
        HttpServer server = null;
        AtomicBoolean activeDuringHandler = new AtomicBoolean();
        AtomicBoolean activeAfterNextEventLoopTick = new AtomicBoolean();
        CountDownLatch nextTick = new CountDownLatch(1);
        try {
            Router router = Router.router(vertx);
            new Ddd4jVertxWeb(
                    new WebRequestContextFactory(),
                    new WebRequestLifecycle(new BearerSubjectAuthenticator(), WebAccessPolicy.disabled()),
                    new DefaultWebExceptionTranslator(), null, io.vertx.core.json.Json::encode).install(router);
            router.get("/scope").handler(context -> {
                activeDuringHandler.set(Span.current().getSpanContext().isValid());
                context.response().end("ok");
                context.vertx().runOnContext(ignored -> {
                    activeAfterNextEventLoopTick.set(Span.current().getSpanContext().isValid());
                    nextTick.countDown();
                });
            });
            server = vertx.createHttpServer().requestHandler(router).listen(0)
                    .toCompletionStage().toCompletableFuture().join();

            HttpURLConnection connection = (HttpURLConnection) new URL(
                    "http://127.0.0.1:" + server.actualPort() + "/scope").openConnection();
            assertEquals(200, connection.getResponseCode());
            connection.disconnect();

            assertTrue(nextTick.await(5, TimeUnit.SECONDS));
            assertTrue(activeDuringHandler.get());
            assertFalse(activeAfterNextEventLoopTick.get());
        } finally {
            if (Objects.nonNull(server)) {
                server.close().toCompletionStage().toCompletableFuture().join();
            }
            vertx.close().toCompletionStage().toCompletableFuture().join();
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
