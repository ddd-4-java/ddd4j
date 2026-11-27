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
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/** Micronaut 3 手动上下文入口的生命周期契约。 */
class Ddd4jMicronautContextTest {

    /**
     * 执行 <code>request Context</code>，调用 <code>Locale.getDefault</code>。
     *
     * @return 当前操作得到的 <code>WebRequestContext</code> 结果
     */
    private WebRequestContext requestContext() {
        return new WebRequestContext("req-1", "trace-1", "tenant-1", null,
                Locale.getDefault(), "127.0.0.1", "GET", "/test");
    }

    /** \u6d4b\u8bd5\u7ed3\u675f\u540e\u6267\u884c <code>ThreadContext.clear()</code>\u3001<code>BaseContext.clear()</code>\u3001<code>PropagatedContext.getOrEmpty()</code> \u5b8c\u6210\u8d44\u6e90\u56de\u6536\u6216\u72b6\u6001\u6062\u590d\u3002 */
    @AfterEach
    void clear() {
        Ddd4jMicronautContext.clear();
        ThreadContext.clear();
        BaseContext.clear();
    }

    /** \u9a8c\u8bc1<code>new Ddd4jMicronautContext(null, Optional.empty())</code> \u629b\u51fa <code>NullPointerException</code>\u3002 */
    @Test
    void constructorRejectsNullRequestContext() {
        assertThrows(NullPointerException.class,
                () -> new Ddd4jMicronautContext(null, Optional.empty()));
    }

    /** \u9a8c\u8bc1<code>new Ddd4jMicronautContext(ctx, null)</code> \u629b\u51fa <code>NullPointerException</code>\u3002 */
    @Test
    void constructorRejectsNullSubject() {
        assertThrows(NullPointerException.class,
                () -> new Ddd4jMicronautContext(requestContext(), null));
    }

    /** \u9a8c\u8bc1<code>propagated.getRequestContext()</code> \u6307\u5411\u540c\u4e00\u5b9e\u4f8b <code>propagated.requestContext()</code>\uff0c<code>propagated.getSubject()</code> \u6307\u5411\u540c\u4e00\u5b9e\u4f8b <code>propagated.subject()</code>\u3002 */
    @Test
    void beanAccessorsExposeTheSameRequestContextAndSubject() {
        WebRequestContext request = requestContext();
        Optional<Subject> subject = Optional.of(mock(Subject.class));
        Ddd4jMicronautContext context = new Ddd4jMicronautContext(request, subject);
        assertSame(context.requestContext(), context.getRequestContext());
        assertSame(context.subject(), context.getSubject());
    }

    /** \u9a8c\u8bc1<code>requestId</code> \u7b49\u4e8e <code>"req-1"</code>\uff0c<code>scope</code>\u975e\u7a7a\u3002 */
    @Test
    void openingScopeWithSubjectBindsRequest() {
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

    /** \u9a8c\u8bc1<code>requestId</code> \u7b49\u4e8e <code>"req-1"</code>\u3002 */
    @Test
    void openingScopeWithoutSubjectBindsRequest() {
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

    /** \u9a8c\u8bc1<code>ThreadContext.getResources()</code>\u975e\u7a7a\uff0c<code>ThreadContext.getResources()</code>\u4e3a\u7a7a\u3002 */
    @Test
    void closingScopeRestoresThreadContext() {
        Ddd4jMicronautContext context =
                new Ddd4jMicronautContext(requestContext(), Optional.empty());
        WebContextScope scope = context.openContext();
        assertFalse(ThreadContext.getResources().isEmpty());
        scope.close();
        assertTrue(ThreadContext.getResources().isEmpty());
    }

    /** \u9a8c\u8bc1<code>Ddd4jMicronautContext.current()</code>\u4e3a\u7a7a\u3002 */
    @Test
    void currentIsEmptyBeforeBinding() {
        Ddd4jMicronautContext.clear();
        assertFalse(Ddd4jMicronautContext.current().isPresent());
    }

    /** \u9a8c\u8bc1<code>found</code>\u5b58\u5728\u503c\uff0c<code>found.get().requestContext()</code> \u7b49\u4e8e <code>ctx</code>\u3002 */
    @Test
    void currentResolvesBoundContext() {
        Ddd4jMicronautContext context =
                new Ddd4jMicronautContext(requestContext(), Optional.empty());
        Ddd4jMicronautContext.set(context);
        assertTrue(Ddd4jMicronautContext.current().isPresent());
        assertEquals(context, Ddd4jMicronautContext.current().get());
        Ddd4jMicronautContext.clear();
        assertFalse(Ddd4jMicronautContext.current().isPresent());
    }

    /** \u9a8c\u8bc1<code>first</code> \u7b49\u4e8e <code>equivalent</code>\uff0c<code>first</code> \u4e0d\u7b49\u4e8e <code>other</code>\uff0c<code>first.toString()</code> \u4ee5\u2026\u5f00\u5934 <code>"Ddd4jMicronautContext["</code>\u3002 */
    @Test
    void contextIdentityIncludesRequestAndSubject() {
        Subject subject = mock(Subject.class);
        Ddd4jMicronautContext first = new Ddd4jMicronautContext(requestContext(), Optional.of(subject));
        Ddd4jMicronautContext equivalent = new Ddd4jMicronautContext(requestContext(), Optional.of(subject));
        Ddd4jMicronautContext other = new Ddd4jMicronautContext(requestContext(), Optional.of(mock(Subject.class)));

        assertEquals(first, equivalent);
        assertEquals(first.hashCode(), equivalent.hashCode());
        assertFalse(first.equals(other));
        assertFalse(first.equals("request"));
        assertEquals(Optional.of(subject), first.subject());
        assertTrue(first.toString().startsWith("Ddd4jMicronautContext["));
    }
}
