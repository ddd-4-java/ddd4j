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
 * 类型说明。
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 3.0.x
 */
class Ddd4jMicronautContextTest {

    /**
     * \u521b\u5efa <code>new WebRequestContext</code> \u4f9b\u5f53\u524d\u6d4b\u8bd5\u8def\u5f84\u8c03\u7528\u3002
     * @return \u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u7ed3\u679c\uff0c\u53d6\u81ea <code>new WebRequestContext("req-1", "trace-1", "tenant-1", null, Locale.getDefault(), "127.0.0.1", "GET", "/test")</code>
     */
    private WebRequestContext createContext() {
        return new WebRequestContext(
                "req-1", "trace-1", "tenant-1", null, Locale.getDefault(),
                "127.0.0.1", "GET", "/test");
    }

    /** \u6d4b\u8bd5\u7ed3\u675f\u540e\u6267\u884c <code>ThreadContext.clear()</code>\u3001<code>BaseContext.clear()</code>\u3001<code>PropagatedContext.getOrEmpty()</code> \u5b8c\u6210\u8d44\u6e90\u56de\u6536\u6216\u72b6\u6001\u6062\u590d\u3002 */
    @AfterEach
    void clear() {
        ThreadContext.clear();
        BaseContext.clear();
        PropagatedContext.getOrEmpty();
    }

    /** \u9a8c\u8bc1<code>new Ddd4jMicronautContext(null, Optional.empty())</code> \u629b\u51fa <code>NullPointerException</code>\u3002 */
    @Test
    void constructorRejectsNullRequestContext() {
        assertThatNullPointerException()
                .isThrownBy(() -> new Ddd4jMicronautContext(null, Optional.empty()));
    }

    /** \u9a8c\u8bc1<code>new Ddd4jMicronautContext(ctx, null)</code> \u629b\u51fa <code>NullPointerException</code>\u3002 */
    @Test
    void constructorRejectsNullSubject() {
        WebRequestContext ctx = createContext();
        assertThatNullPointerException()
                .isThrownBy(() -> new Ddd4jMicronautContext(ctx, null));
    }

    /** \u9a8c\u8bc1<code>propagated.getRequestContext()</code> \u6307\u5411\u540c\u4e00\u5b9e\u4f8b <code>propagated.requestContext()</code>\uff0c<code>propagated.getSubject()</code> \u6307\u5411\u540c\u4e00\u5b9e\u4f8b <code>propagated.subject()</code>\u3002 */
    @Test
    void beanAccessorsExposeTheSameRequestContextAndSubject() {
        WebRequestContext ctx = createContext();
        Optional<Subject> subject = Optional.of(org.mockito.Mockito.mock(Subject.class));
        Ddd4jMicronautContext propagated = new Ddd4jMicronautContext(ctx, subject);

        assertThat(propagated.getRequestContext()).isSameAs(propagated.requestContext());
        assertThat(propagated.getSubject()).isSameAs(propagated.subject());
    }

    /** \u9a8c\u8bc1<code>requestId</code> \u7b49\u4e8e <code>"req-1"</code>\uff0c<code>scope</code>\u975e\u7a7a\u3002 */
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

    /** \u9a8c\u8bc1<code>requestId</code> \u7b49\u4e8e <code>"req-1"</code>\u3002 */
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

    /** \u9a8c\u8bc1<code>ThreadContext.getResources()</code>\u975e\u7a7a\uff0c<code>ThreadContext.getResources()</code>\u4e3a\u7a7a\u3002 */
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

    /** \u9a8c\u8bc1<code>Ddd4jMicronautContext.current()</code>\u4e3a\u7a7a\u3002 */
    @Test
    void currentIsEmptyBeforeBinding() {
        assertThat(Ddd4jMicronautContext.current()).isEmpty();
    }

    /** \u9a8c\u8bc1<code>found</code>\u5b58\u5728\u503c\uff0c<code>found.get().requestContext()</code> \u7b49\u4e8e <code>ctx</code>\u3002 */
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

    /** \u9a8c\u8bc1<code>first</code> \u7b49\u4e8e <code>equivalent</code>\uff0c<code>first</code> \u4e0d\u7b49\u4e8e <code>other</code>\uff0c<code>first.toString()</code> \u4ee5\u2026\u5f00\u5934 <code>"Ddd4jMicronautContext["</code>\u3002 */
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
