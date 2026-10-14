package io.ddd4j.web.micronaut;

import io.ddd4j.core.context.BaseContext;
import io.ddd4j.core.context.ThreadContext;
import io.ddd4j.core.subject.Subject;
import io.ddd4j.web.core.context.WebContextScope;
import io.ddd4j.web.core.context.WebRequestContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Locale;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/** Micronaut 3 手动上下文入口的生命周期契约。 */
class Ddd4jMicronautContextTest {

    private WebRequestContext requestContext() {
        return new WebRequestContext("req-1", "trace-1", "tenant-1", null,
                Locale.getDefault(), "127.0.0.1", "GET", "/test");
    }

    @AfterEach
    void clear() {
        Ddd4jMicronautContext.clear();
        ThreadContext.clear();
        BaseContext.clear();
    }

    @Test
    void constructorRejectsNullRequestContext() {
        assertThrows(NullPointerException.class,
                () -> new Ddd4jMicronautContext(null, Optional.empty()));
    }

    @Test
    void constructorRejectsNullSubject() {
        assertThrows(NullPointerException.class,
                () -> new Ddd4jMicronautContext(requestContext(), null));
    }

    @Test
    void openContextWithSubjectOpensRequestScope() {
        Ddd4jMicronautContext context =
                new Ddd4jMicronautContext(requestContext(), Optional.of(mock(Subject.class)));
        WebContextScope scope = context.openContext();
        try {
            assertEquals("req-1", ThreadContext.get(WebContextScope.REQUEST_ID));
        } finally {
            scope.close();
        }
        assertTrue(ThreadContext.getResources().isEmpty());
    }

    @Test
    void openContextWithoutSubjectOpensRequestScope() {
        Ddd4jMicronautContext context =
                new Ddd4jMicronautContext(requestContext(), Optional.empty());
        WebContextScope scope = context.openContext();
        try {
            assertEquals("req-1", ThreadContext.get(WebContextScope.REQUEST_ID));
        } finally {
            scope.close();
        }
        assertTrue(ThreadContext.getResources().isEmpty());
    }

    @Test
    void currentReturnsEmptyWhenNotSet() {
        Ddd4jMicronautContext.clear();
        assertFalse(Ddd4jMicronautContext.current().isPresent());
    }

    @Test
    void setAndClearBoundContext() {
        Ddd4jMicronautContext context =
                new Ddd4jMicronautContext(requestContext(), Optional.empty());
        Ddd4jMicronautContext.set(context);
        assertTrue(Ddd4jMicronautContext.current().isPresent());
        assertEquals(context, Ddd4jMicronautContext.current().get());
        Ddd4jMicronautContext.clear();
        assertFalse(Ddd4jMicronautContext.current().isPresent());
    }
}
