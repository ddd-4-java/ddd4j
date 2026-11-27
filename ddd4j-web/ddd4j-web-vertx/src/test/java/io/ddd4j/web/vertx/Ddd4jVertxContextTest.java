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
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.Callable;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 验证 <code>Ddd4jVertxContext</code> 在<code>request</code>、<code>subject</code>、<code>executeBlockingRunsTask</code>、<code>executeBlockingFails</code>等操作中的行为契约。
 */
@ExtendWith(MockitoExtension.class)
class Ddd4jVertxContextTest {

    /**
     * 用来在测试请求上下文中保存或检查属性的键。
     */
    private static final String REQUEST_KEY = Ddd4jVertxContext.class.getName() + ".request";
    /**
     * 用来检查请求主体是否已经正确传播的上下文键。
     */
    private static final String SUBJECT_KEY = Ddd4jVertxContext.class.getName() + ".subject";

    /**
     * 用于隔离 <code>RoutingContext</code> 调用并记录交互的测试替身。
     */
    @Mock
    private RoutingContext context;

    /**
     * 用于隔离 <code>Vertx</code> 调用并记录交互的测试替身。
     */
    @Mock
    private Vertx vertx;

    /**
     * 创建 <code>new WebRequestContext</code> 供当前测试路径调用。
     *
     * @return 当前测试使用的结果，取自 <code>new WebRequestContext("r-1", "t-1", "tenant-a", "Bearer token", Locale.CHINA, "127.0.0.1", "GET", "/api")</code>
     */
    private static WebRequestContext requestContext() {
        return new WebRequestContext("r-1", "t-1", "tenant-a", "Bearer token",
                Locale.CHINA, "127.0.0.1", "GET", "/api");
    }

    /**
     * 验证<code>Ddd4jVertxContext.request(context)</code> 等于 <code>Optional.of(stored)</code>。
     */
    @Test
    void requestReturnsStoredContext() {
        WebRequestContext stored = requestContext();
        when(context.get(REQUEST_KEY)).thenReturn(stored);

        assertEquals(Optional.of(stored), Ddd4jVertxContext.request(context));
    }

    /**
     * 验证<code>Ddd4jVertxContext.request(context).isPresent()</code>为假。
     */
    @Test
    void requestReturnsEmptyWhenAbsent() {
        when(context.get(REQUEST_KEY)).thenReturn(null);
        assertFalse(Ddd4jVertxContext.request(context).isPresent());
    }

    /**
     * 验证<code>Ddd4jVertxContext.subject(context)</code> 等于 <code>Optional.of(subject)</code>。
     */
    @Test
    void subjectReturnsStoredSubject() {
        Subject subject = mock(Subject.class);
        when(context.get(SUBJECT_KEY)).thenReturn(subject);

        assertEquals(Optional.of(subject), Ddd4jVertxContext.subject(context));
    }

    /**
     * 验证<code>Ddd4jVertxContext.request(null)</code> 抛出 <code>NullPointerException.class</code>，<code>Ddd4jVertxContext.subject(null)</code> 抛出 <code>NullPointerException.class</code>。
     */
    @Test
    void requestRejectsNullContext() {
        assertThrows(NullPointerException.class, () -> Ddd4jVertxContext.request(null));
        assertThrows(NullPointerException.class, () -> Ddd4jVertxContext.subject(null));
    }

    /**
     * 验证<code>future.succeeded()</code>为真，<code>future.result()</code> 等于 <code>"done"</code>。
     */
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

    /**
     * 验证<code>Ddd4jVertxContext.executeBlocking(context, task)</code> 抛出 <code>IllegalStateException.class</code>。
     */
    @Test
    void executeBlockingFailsWhenRequestContextMissing() {
        Callable<String> task = () -> "done";
        assertThrows(IllegalStateException.class, () -> Ddd4jVertxContext.executeBlocking(context, task));
    }

    /**
     * 验证<code>Ddd4jVertxContext.executeBlocking(context, null)</code> 抛出 <code>NullPointerException.class</code>。
     */
    @Test
    void executeBlockingRejectsNullTask() {
        assertThrows(NullPointerException.class, () -> Ddd4jVertxContext.executeBlocking(context, null));
    }

    /**
     * 在测试框架中执行 <code>requestContext()</code>、<code>Ddd4jVertxContext.bindRequest()</code>，验证该场景。
     */
    @Test
    void bindRequestStoresRequest() {
        WebRequestContext stored = requestContext();
        Ddd4jVertxContext.bindRequest(context, stored);
        verify(context).put(REQUEST_KEY, stored);
    }

    /**
     * 在测试框架中执行 <code>mock()</code>、<code>Ddd4jVertxContext.bindSubject()</code>，验证该场景。
     */
    @Test
    void bindSubjectStoresSubject() {
        Subject subject = mock(Subject.class);
        Ddd4jVertxContext.bindSubject(context, subject);
        verify(context).put(SUBJECT_KEY, subject);
    }

    /**
     * 验证<code>future.failed()</code>为真，<code>future.cause().getMessage()</code> 等于 <code>"task failed"</code>。
     */
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
