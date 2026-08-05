package io.ddd4j.web.micronaut;

import io.ddd4j.core.context.ThreadContext;
import io.ddd4j.core.subject.Subject;
import io.ddd4j.web.core.context.WebContextScope;
import io.ddd4j.web.core.context.WebRequestContext;
import io.micronaut.core.propagation.PropagatedContext;
import io.micronaut.core.propagation.ThreadPropagatedContextElement;

import java.util.Objects;
import java.util.Optional;

/**
 * 通过 Micronaut PropagatedContext 传播 ddd4j 请求上下文。
 */
public record Ddd4jMicronautContext(WebRequestContext requestContext, Optional<Subject> subject)
        implements ThreadPropagatedContextElement<WebContextScope> {

    public Ddd4jMicronautContext {
        Objects.requireNonNull(requestContext, "requestContext must not be null");
        subject = Objects.requireNonNull(subject, "subject must not be null");
    }

    public static Optional<Ddd4jMicronautContext> current() {
        return PropagatedContext.getOrEmpty().find(Ddd4jMicronautContext.class);
    }

    @Override
    public WebContextScope updateThreadContext() {
        WebContextScope scope = WebContextScope.open(requestContext);
        subject.ifPresent(ThreadContext::bind);
        return scope;
    }

    @Override
    public void restoreThreadContext(WebContextScope oldState) {
        oldState.close();
    }
}
