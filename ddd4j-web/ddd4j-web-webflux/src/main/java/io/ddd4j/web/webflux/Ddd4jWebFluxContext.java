package io.ddd4j.web.webflux;

import io.ddd4j.core.subject.Subject;
import io.ddd4j.web.core.context.WebRequestContext;
import lombok.experimental.UtilityClass;
import reactor.core.publisher.Mono;

/**
 * 从 Reactor Context 读取 ddd4j 请求级状态。
 */
@UtilityClass
public class Ddd4jWebFluxContext {

    static final Class<WebRequestContext> REQUEST_CONTEXT_KEY = WebRequestContext.class;
    static final Class<Subject> SUBJECT_KEY = Subject.class;

    public Mono<WebRequestContext> currentRequest() {
        return Mono.deferContextual(context -> Mono.justOrEmpty(context.getOrEmpty(REQUEST_CONTEXT_KEY)));
    }

    public Mono<Subject> currentSubject() {
        return Mono.deferContextual(context -> Mono.justOrEmpty(context.getOrEmpty(SUBJECT_KEY)));
    }
}
