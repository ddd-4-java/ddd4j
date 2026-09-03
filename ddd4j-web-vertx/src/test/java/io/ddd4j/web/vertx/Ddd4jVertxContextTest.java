package io.ddd4j.web.vertx;

import io.ddd4j.core.subject.Subject;
import io.ddd4j.web.core.context.WebRequestContext;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.ext.web.RoutingContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.Callable;


import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class Ddd4jVertxContextTest {

    private static final String REQUEST_KEY = Ddd4jVertxContext.class.getName() + ".request";
    private static final String SUBJECT_KEY = Ddd4jVertxContext.class.getName() + ".subject";

    @Mock
    private RoutingContext context;

    @Mock
    private Vertx vertx;

    private static WebRequestContext requestContext() {
        return new WebRequestContext("r-1", "t-1", "tenant-a", "Bearer token",
                Locale.CHINA, "127.0.0.1", "GET", "/api");
    }

    @Test
    void requestReturnsStoredContext() {
        WebRequestContext stored = requestContext();
        when(context.get(REQUEST_KEY)).thenReturn(stored);

        assertEquals(Optional.of(stored), Ddd4jVertxContext.request(context));
    }

    @Test
    void requestReturnsEmptyWhenAbsent() {
        when(context.get(REQUEST_KEY)).thenReturn(null);
        assertFalse(Ddd4jVertxContext.request(context).isPresent());
    }

    @Test
    void subjectReturnsStoredSubject() {
        Subject subject = mock(Subject.class);
        when(context.get(SUBJECT_KEY)).thenReturn(subject);

        assertEquals(Optional.of(subject), Ddd4jVertxContext.subject(context));
    }

    @Test
    void requestRejectsNullContext() {
        assertThrows(NullPointerException.class, () -> Ddd4jVertxContext.request(null));
        assertThrows(NullPointerException.class, () -> Ddd4jVertxContext.subject(null));
    }

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
        verify(context).put(REQUEST_KEY, stored);
    }

    @Test
    void bindSubjectStoresSubject() {
        Subject subject = mock(Subject.class);
        Ddd4jVertxContext.bindSubject(context, subject);
        verify(context).put(SUBJECT_KEY, subject);
    }

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
