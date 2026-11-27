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
package io.ddd4j.web.micronaut;

import io.ddd4j.core.context.BaseContext;
import io.ddd4j.core.context.ThreadContext;
import io.ddd4j.core.subject.Subject;
import io.ddd4j.web.core.context.WebContextScope;
import io.ddd4j.web.core.context.WebRequestContext;
import io.micronaut.core.propagation.PropagatedContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Locale;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

/**
 * 验证 <code>Ddd4jMicronautContext</code> 在<code>constructor</code>、<code>openingScope</code>等操作中的行为契约。
 */
class Ddd4jMicronautContextTest {

    /**
     * 创建 <code>new WebRequestContext</code> 供当前测试路径调用。
     *
     * @return 当前测试使用的结果，取自 <code>new WebRequestContext("req-1", "trace-1", "tenant-1", null, Locale.getDefault(), "127.0.0.1", "GET", "/test")</code>
     */
    private WebRequestContext createContext() {
        return new WebRequestContext(
                "req-1", "trace-1", "tenant-1", null, Locale.getDefault(),
                "127.0.0.1", "GET", "/test");
    }

    /**
     * 测试结束后执行 <code>ThreadContext.clear()</code>、<code>BaseContext.clear()</code>、<code>PropagatedContext.getOrEmpty()</code> 完成资源回收或状态恢复。
     */
    @AfterEach
    void clear() {
        ThreadContext.clear();
        BaseContext.clear();
        PropagatedContext.getOrEmpty();
    }

    /**
     * 验证<code>new Ddd4jMicronautContext(null, Optional.empty())</code> 抛出 <code>NullPointerException</code>。
     */
    @Test
    void constructorRejectsNullRequestContext() {
        assertThatNullPointerException()
                .isThrownBy(() -> new Ddd4jMicronautContext(null, Optional.empty()));
    }

    /**
     * 验证<code>new Ddd4jMicronautContext(ctx, null)</code> 抛出 <code>NullPointerException</code>。
     */
    @Test
    void constructorRejectsNullSubject() {
        WebRequestContext ctx = createContext();
        assertThatNullPointerException()
                .isThrownBy(() -> new Ddd4jMicronautContext(ctx, null));
    }

    /**
     * 验证<code>propagated.getRequestContext()</code> 指向同一实例 <code>propagated.requestContext()</code>，<code>propagated.getSubject()</code> 指向同一实例 <code>propagated.subject()</code>。
     */
    @Test
    void beanAccessorsExposeTheSameRequestContextAndSubject() {
        WebRequestContext ctx = createContext();
        Optional<Subject> subject = Optional.of(org.mockito.Mockito.mock(Subject.class));
        Ddd4jMicronautContext propagated = new Ddd4jMicronautContext(ctx, subject);

        assertThat(propagated.getRequestContext()).isSameAs(propagated.requestContext());
        assertThat(propagated.getSubject()).isSameAs(propagated.subject());
    }

    /**
     * 验证<code>requestId</code> 等于 <code>"req-1"</code>，<code>scope</code>非空。
     */
    @Test
    void openingScopeWithSubjectBindsRequest() {
        WebRequestContext ctx = createContext();
        Subject subject = org.mockito.Mockito.mock(Subject.class);
        Ddd4jMicronautContext propagated =
                new Ddd4jMicronautContext(ctx, Optional.of(subject));

        WebContextScope scope = propagated.updateThreadContext();

        try {
            String requestId = (String) ThreadContext.get(WebContextScope.REQUEST_ID);
            assertThat(requestId).isEqualTo("req-1");
            assertThat(scope).isNotNull();
        } finally {
            scope.close();
        }
    }

    /**
     * 验证<code>requestId</code> 等于 <code>"req-1"</code>。
     */
    @Test
    void openingScopeWithoutSubjectBindsRequest() {
        WebRequestContext ctx = createContext();
        Ddd4jMicronautContext propagated =
                new Ddd4jMicronautContext(ctx, Optional.empty());

        WebContextScope scope = propagated.updateThreadContext();

        try {
            String requestId = (String) ThreadContext.get(WebContextScope.REQUEST_ID);
            assertThat(requestId).isEqualTo("req-1");
        } finally {
            scope.close();
        }
    }

    /**
     * 验证<code>ThreadContext.getResources()</code>非空，<code>ThreadContext.getResources()</code>为空。
     */
    @Test
    void closingScopeRestoresThreadContext() {
        WebRequestContext ctx = createContext();
        Ddd4jMicronautContext propagated =
                new Ddd4jMicronautContext(ctx, Optional.empty());

        WebContextScope oldScope = propagated.updateThreadContext();
        assertThat(ThreadContext.getResources()).isNotEmpty();

        propagated.restoreThreadContext(oldScope);

        assertThat(ThreadContext.getResources()).isEmpty();
    }

    /**
     * 验证<code>Ddd4jMicronautContext.current()</code>为空。
     */
    @Test
    void currentIsEmptyBeforeBinding() {
        assertThat(Ddd4jMicronautContext.current()).isEmpty();
    }

    /**
     * 验证<code>found</code>存在值，<code>found.get().requestContext()</code> 等于 <code>ctx</code>。
     */
    @Test
    void currentResolvesBoundContext() {
        WebRequestContext ctx = createContext();
        Ddd4jMicronautContext propagated =
                new Ddd4jMicronautContext(ctx, Optional.empty());

        try (PropagatedContext.Scope ignored = PropagatedContext.getOrEmpty()
                .plus(propagated).propagate()) {
            Optional<Ddd4jMicronautContext> found = Ddd4jMicronautContext.current();
            assertThat(found).isPresent();
            assertThat(found.get().requestContext()).isEqualTo(ctx);
        }
    }

    /**
     * 验证<code>first</code> 等于 <code>equivalent</code>，<code>first</code> 不等于 <code>other</code>，<code>first.toString()</code> 以…开头 <code>"Ddd4jMicronautContext["</code>。
     */
    @Test
    void contextIdentityIncludesRequestAndSubject() {
        WebRequestContext request = createContext();
        Subject firstSubject = org.mockito.Mockito.mock(Subject.class);
        Subject otherSubject = org.mockito.Mockito.mock(Subject.class);
        Ddd4jMicronautContext first = new Ddd4jMicronautContext(request, Optional.of(firstSubject));
        Ddd4jMicronautContext equivalent = new Ddd4jMicronautContext(createContext(), Optional.of(firstSubject));
        Ddd4jMicronautContext other = new Ddd4jMicronautContext(request, Optional.of(otherSubject));

        assertThat(first).isEqualTo(equivalent).hasSameHashCodeAs(equivalent);
        assertThat(first).isNotEqualTo(other).isNotEqualTo("request");
        assertThat(first.subject()).contains(firstSubject);
        assertThat(first.toString()).startsWith("Ddd4jMicronautContext[");
    }
}
