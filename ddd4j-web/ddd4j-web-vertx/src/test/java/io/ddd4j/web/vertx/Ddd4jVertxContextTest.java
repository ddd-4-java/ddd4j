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

import io.ddd4j.core.subject.Subject;
import io.ddd4j.web.core.context.WebRequestContext;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.ext.web.RoutingContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;

import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Callable;


import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Ddd4jVertxContextTest {

    private static final String REQUEST_KEY = Ddd4jVertxContext.class.getName() + ".request";
    private static final String SUBJECT_KEY = Ddd4jVertxContext.class.getName() + ".subject";

    private RoutingContext context;

    private Map<String, Object> contextValues;

    private Vertx vertx;

    @BeforeEach
    void setUpRoutingContext() {
        contextValues = new HashMap<>();
        vertx = (Vertx) Proxy.newProxyInstance(
                Vertx.class.getClassLoader(), new Class<?>[]{Vertx.class},
                (proxy, method, arguments) -> {
                    if ("executeBlocking".equals(method.getName())
                            && arguments[0] instanceof Callable) {
                        try {
                            return Future.succeededFuture(((Callable<?>) arguments[0]).call());
                        } catch (Exception exception) {
                            return Future.failedFuture(exception);
                        }
                    }
                    return null;
                });
        context = (RoutingContext) Proxy.newProxyInstance(
                RoutingContext.class.getClassLoader(), new Class<?>[]{RoutingContext.class},
                (proxy, method, arguments) -> {
                    if ("get".equals(method.getName())) {
                        return contextValues.get(arguments[0]);
                    }
                    if ("put".equals(method.getName())) {
                        contextValues.put((String) arguments[0], arguments[1]);
                        return proxy;
                    }
                    if ("vertx".equals(method.getName())) {
                        return vertx;
                    }
                    return null;
                });
    }

    private static WebRequestContext requestContext() {
        return new WebRequestContext("r-1", "t-1", "tenant-a", "Bearer token",
                Locale.CHINA, "127.0.0.1", "GET", "/api");
    }

    @Test
    void requestReturnsStoredContext() {
        WebRequestContext stored = requestContext();
        contextValues.put(REQUEST_KEY, stored);

        assertEquals(Optional.of(stored), Ddd4jVertxContext.request(context));
    }

    @Test
    void requestReturnsEmptyWhenAbsent() {
        assertFalse(Ddd4jVertxContext.request(context).isPresent());
    }

    @Test
    void subjectReturnsStoredSubject() {
        Subject subject = subjectStub();
        contextValues.put(SUBJECT_KEY, subject);

        assertEquals(Optional.of(subject), Ddd4jVertxContext.subject(context));
    }

    @Test
    void requestRejectsNullContext() {
        assertThrows(NullPointerException.class, () -> Ddd4jVertxContext.request(null));
        assertThrows(NullPointerException.class, () -> Ddd4jVertxContext.subject(null));
    }

    @Test
    void executeBlockingRunsTaskWithBoundRequest() {
        contextValues.put(REQUEST_KEY, requestContext());

        Future<String> future = Ddd4jVertxContext.executeBlocking(context, () -> "done");

        assertTrue(future.succeeded());
        assertEquals("done", future.result());
    }

    @Test
    void executeBlockingFailsWhenRequestContextMissing() {
        Callable<String> task = () -> "done";
        assertThrows(IllegalStateException.class, () -> Ddd4jVertxContext.executeBlocking(context, task));
    }

    @Test
    void executeBlockingRejectsNullTask() {
        assertThrows(NullPointerException.class, () -> Ddd4jVertxContext.executeBlocking(context, null));
    }

    @Test
    void bindRequestStoresRequest() {
        WebRequestContext stored = requestContext();
        Ddd4jVertxContext.bindRequest(context, stored);
        assertEquals(stored, contextValues.get(REQUEST_KEY));
    }

    @Test
    void bindSubjectStoresSubject() {
        Subject subject = subjectStub();
        Ddd4jVertxContext.bindSubject(context, subject);
        assertEquals(subject, contextValues.get(SUBJECT_KEY));
    }

    @Test
    void executeBlockingPropagatesTaskFailure() {
        contextValues.put(REQUEST_KEY, requestContext());

        Future<String> future = Ddd4jVertxContext.executeBlocking(context, () -> {
            throw new IllegalStateException("task failed");
        });

        assertTrue(future.failed());
        assertEquals("task failed", future.cause().getMessage());
    }

    private Subject subjectStub() {
        return (Subject) Proxy.newProxyInstance(
                Subject.class.getClassLoader(), new Class<?>[]{Subject.class},
                (proxy, method, arguments) -> {
                    if ("equals".equals(method.getName())) {
                        return proxy == arguments[0];
                    }
                    if ("hashCode".equals(method.getName())) {
                        return System.identityHashCode(proxy);
                    }
                    return null;
                });
    }
}
