package io.ddd4j.web.vertx;

import io.ddd4j.core.context.ThreadContext;
import io.ddd4j.core.subject.Subject;
import io.ddd4j.web.core.WebContextScope;
import io.ddd4j.web.core.WebRequestContext;
import io.vertx.core.Future;
import io.vertx.ext.web.RoutingContext;
import lombok.experimental.UtilityClass;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Callable;

/**
 * Vert.x 请求状态访问与阻塞领域用例桥接。
 */
@UtilityClass
public class Ddd4jVertxContext {

    private static final String REQUEST_CONTEXT_KEY = Ddd4jVertxContext.class.getName() + ".request";
    private static final String SUBJECT_KEY = Ddd4jVertxContext.class.getName() + ".subject";

    public Optional<WebRequestContext> request(RoutingContext context) {
        return Optional.ofNullable(Objects.requireNonNull(context, "context must not be null")
                .get(REQUEST_CONTEXT_KEY));
    }

    public Optional<Subject> subject(RoutingContext context) {
        return Optional.ofNullable(Objects.requireNonNull(context, "context must not be null").get(SUBJECT_KEY));
    }

    public <T> Future<T> executeBlocking(RoutingContext context, Callable<T> task) {
        RoutingContext routingContext = Objects.requireNonNull(context, "context must not be null");
        Callable<T> actualTask = Objects.requireNonNull(task, "task must not be null");
        WebRequestContext requestContext = request(routingContext)
                .orElseThrow(() -> new IllegalStateException("ddd4j request context is unavailable"));
        Optional<Subject> currentSubject = subject(routingContext);
        return routingContext.vertx().executeBlocking(() -> {
            try (WebContextScope ignored = WebContextScope.open(requestContext)) {
                currentSubject.ifPresent(ThreadContext::bind);
                return actualTask.call();
            }
        });
    }

    static void bindRequest(RoutingContext context, WebRequestContext requestContext) {
        context.put(REQUEST_CONTEXT_KEY, requestContext);
    }

    static void bindSubject(RoutingContext context, Subject subject) {
        context.put(SUBJECT_KEY, subject);
    }
}
