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

class Ddd4jMicronautContextTest {

    private WebRequestContext createContext() {
        return new WebRequestContext(
                "req-1", "trace-1", "tenant-1", null, Locale.getDefault(),
                "127.0.0.1", "GET", "/test");
    }

    @AfterEach
    void clear() {
        ThreadContext.clear();
        BaseContext.clear();
        PropagatedContext.getOrEmpty();
    }

    @Test
    void constructorRejectsNullRequestContext() {
        assertThatNullPointerException()
                .isThrownBy(() -> new Ddd4jMicronautContext(null, Optional.empty()));
    }

    @Test
    void constructorRejectsNullSubject() {
        WebRequestContext ctx = createContext();
        assertThatNullPointerException()
                .isThrownBy(() -> new Ddd4jMicronautContext(ctx, null));
    }

    @Test
    void updateThreadContextOpensScopeAndBindsSubject() {
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

    @Test
    void updateThreadContextWithoutSubjectOnlyOpensScope() {
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

    @Test
    void restoreThreadContextClosesOldScope() {
        WebRequestContext ctx = createContext();
        Ddd4jMicronautContext propagated =
                new Ddd4jMicronautContext(ctx, Optional.empty());

        WebContextScope oldScope = propagated.updateThreadContext();
        assertThat(ThreadContext.getResources()).isNotEmpty();

        propagated.restoreThreadContext(oldScope);

        assertThat(ThreadContext.getResources()).isEmpty();
    }

    @Test
    void currentReturnsEmptyWhenNotPropagated() {
        assertThat(Ddd4jMicronautContext.current()).isEmpty();
    }

    @Test
    void currentReturnsContextWhenPropagated() {
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
}