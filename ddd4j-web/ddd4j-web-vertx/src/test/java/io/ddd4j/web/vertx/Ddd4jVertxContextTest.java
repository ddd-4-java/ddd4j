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
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.mock;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;

import io.ddd4j.core.subject.Subject;
import io.ddd4j.web.core.context.WebRequestContext;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.ext.web.RoutingContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.Callable;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** \u9a8c\u8bc1 <code>Ddd4jVertxContext</code> \u5728<code>request</code>\u3001<code>subject</code>\u3001<code>executeBlockingRunsTask</code>\u3001<code>executeBlockingFails</code>\u7b49\u64cd\u4f5c\u4e2d\u7684\u884c\u4e3a\u5951\u7ea6\u3002 */
@ExtendWith(MockitoExtension.class)
class Ddd4jVertxContextTest {

    /** \u7528\u6765\u5728\u6d4b\u8bd5\u8bf7\u6c42\u4e0a\u4e0b\u6587\u4e2d\u4fdd\u5b58\u6216\u68c0\u67e5\u5c5e\u6027\u7684\u952e\u3002 */
    private static final String REQUEST_KEY = Ddd4jVertxContext.class.getName() + ".request";
    /** \u7528\u6765\u68c0\u67e5\u8bf7\u6c42\u4e3b\u4f53\u662f\u5426\u5df2\u7ecf\u6b63\u786e\u4f20\u64ad\u7684\u4e0a\u4e0b\u6587\u952e\u3002 */
    private static final String SUBJECT_KEY = Ddd4jVertxContext.class.getName() + ".subject";

    /** \u7528\u4e8e\u9694\u79bb <code>RoutingContext</code> \u8c03\u7528\u5e76\u8bb0\u5f55\u4ea4\u4e92\u7684\u6d4b\u8bd5\u66ff\u8eab\u3002 */
    @Mock
    private RoutingContext context;

    /** \u7528\u4e8e\u9694\u79bb <code>Vertx</code> \u8c03\u7528\u5e76\u8bb0\u5f55\u4ea4\u4e92\u7684\u6d4b\u8bd5\u66ff\u8eab\u3002 */
    @Mock
    private Vertx vertx;

    /**
     * \u521b\u5efa <code>new WebRequestContext</code> \u4f9b\u5f53\u524d\u6d4b\u8bd5\u8def\u5f84\u8c03\u7528\u3002
     * @return \u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u7ed3\u679c\uff0c\u53d6\u81ea <code>new WebRequestContext("r-1", "t-1", "tenant-a", "Bearer token", Locale.CHINA, "127.0.0.1", "GET", "/api")</code>
     */
    private static WebRequestContext requestContext() {
        return new WebRequestContext("r-1", "t-1", "tenant-a", "Bearer token",
                Locale.CHINA, "127.0.0.1", "GET", "/api");
    }

    /** \u9a8c\u8bc1<code>Ddd4jVertxContext.request(context)</code> \u7b49\u4e8e <code>Optional.of(stored)</code>\u3002 */
    @Test
    void requestReturnsStoredContext() {
        WebRequestContext stored = requestContext();
        when(context.get(REQUEST_KEY)).thenReturn(stored);

        assertEquals(Optional.of(stored), Ddd4jVertxContext.request(context));
    }

    /** \u9a8c\u8bc1<code>Ddd4jVertxContext.request(context).isPresent()</code>\u4e3a\u5047\u3002 */
    @Test
    void requestReturnsEmptyWhenAbsent() {
        when(context.get(REQUEST_KEY)).thenReturn(null);
        assertFalse(Ddd4jVertxContext.request(context).isPresent());
    }

    /** \u9a8c\u8bc1<code>Ddd4jVertxContext.subject(context)</code> \u7b49\u4e8e <code>Optional.of(subject)</code>\u3002 */
    @Test
    void subjectReturnsStoredSubject() {
        Subject subject = mock(Subject.class);
        when(context.get(SUBJECT_KEY)).thenReturn(subject);

        assertEquals(Optional.of(subject), Ddd4jVertxContext.subject(context));
    }

    /** \u9a8c\u8bc1<code>Ddd4jVertxContext.request(null)</code> \u629b\u51fa <code>NullPointerException.class</code>\uff0c<code>Ddd4jVertxContext.subject(null)</code> \u629b\u51fa <code>NullPointerException.class</code>\u3002 */
    @Test
    void requestRejectsNullContext() {
        assertThrows(NullPointerException.class, () -> Ddd4jVertxContext.request(null));
        assertThrows(NullPointerException.class, () -> Ddd4jVertxContext.subject(null));
    }

    /** \u9a8c\u8bc1<code>future.succeeded()</code>\u4e3a\u771f\uff0c<code>future.result()</code> \u7b49\u4e8e <code>"done"</code>\u3002 */
    @Test
    void executeBlockingRunsTaskWithBoundRequest() {
        when(context.get(REQUEST_KEY)).thenReturn(requestContext());
        when(context.vertx()).thenReturn(vertx);
        when(vertx.executeBlocking(any(Callable.class))).thenAnswer(invocation -> {
            Callable<String> callable = invocation.getArgument(0);
            try {
                return Future.succeededFuture(callable.call());
            } catch (Exception e) {
                return Future.failedFuture(e);
            }
        });

        Future<String> future = Ddd4jVertxContext.executeBlocking(context, () -> "done");

        assertTrue(future.succeeded());
        assertEquals("done", future.result());
    }

    /** \u9a8c\u8bc1<code>Ddd4jVertxContext.executeBlocking(context, task)</code> \u629b\u51fa <code>IllegalStateException.class</code>\u3002 */
    @Test
    void executeBlockingFailsWhenRequestContextMissing() {
        Callable<String> task = () -> "done";
        assertThrows(IllegalStateException.class, () -> Ddd4jVertxContext.executeBlocking(context, task));
    }

    /** \u9a8c\u8bc1<code>Ddd4jVertxContext.executeBlocking(context, null)</code> \u629b\u51fa <code>NullPointerException.class</code>\u3002 */
    @Test
    void executeBlockingRejectsNullTask() {
        assertThrows(NullPointerException.class, () -> Ddd4jVertxContext.executeBlocking(context, null));
    }

    /** \u5728\u6d4b\u8bd5\u6846\u67b6\u4e2d\u6267\u884c <code>requestContext()</code>\u3001<code>Ddd4jVertxContext.bindRequest()</code>\uff0c\u9a8c\u8bc1\u8be5\u573a\u666f\u3002 */
    @Test
    void bindRequestStoresRequest() {
        WebRequestContext stored = requestContext();
        Ddd4jVertxContext.bindRequest(context, stored);
        verify(context).put(REQUEST_KEY, stored);
    }

    /** \u5728\u6d4b\u8bd5\u6846\u67b6\u4e2d\u6267\u884c <code>mock()</code>\u3001<code>Ddd4jVertxContext.bindSubject()</code>\uff0c\u9a8c\u8bc1\u8be5\u573a\u666f\u3002 */
    @Test
    void bindSubjectStoresSubject() {
        Subject subject = mock(Subject.class);
        Ddd4jVertxContext.bindSubject(context, subject);
        verify(context).put(SUBJECT_KEY, subject);
    }

    /** \u9a8c\u8bc1<code>future.failed()</code>\u4e3a\u771f\uff0c<code>future.cause().getMessage()</code> \u7b49\u4e8e <code>"task failed"</code>\u3002 */
    @Test
    void executeBlockingPropagatesTaskFailure() {
        when(context.get(REQUEST_KEY)).thenReturn(requestContext());
        when(context.vertx()).thenReturn(vertx);
        when(vertx.executeBlocking(any(Callable.class))).thenAnswer(invocation -> {
            Callable<String> callable = invocation.getArgument(0);
            try {
                return Future.succeededFuture(callable.call());
            } catch (Exception e) {
                return Future.failedFuture(e);
            }
        });

        Future<String> future = Ddd4jVertxContext.executeBlocking(context, () -> {
            throw new IllegalStateException("task failed");
        });

        assertTrue(future.failed());
        assertEquals("task failed", future.cause().getMessage());
    }
}
