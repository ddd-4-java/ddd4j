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

    /** \u5728\u6846\u67b6\u8bf7\u6c42\u4e2d\u4fdd\u5b58 DDD4J \u8bf7\u6c42\u4e0a\u4e0b\u6587\u7684\u952e\u3002 */
    static final Class<WebRequestContext> REQUEST_CONTEXT_KEY = WebRequestContext.class;
    /** \u5728\u6846\u67b6\u8bf7\u6c42\u4e2d\u4fdd\u5b58\u8ba4\u8bc1\u4e3b\u4f53\u7684\u952e\u3002 */
    static final Class<Subject> SUBJECT_KEY = Subject.class;

    /**
     * \u8bfb\u53d6\u5f53\u524d Reactor \u4e0a\u4e0b\u6587\u4e2d\u7684 Web \u8bf7\u6c42\u72b6\u6001\u3002
     * @return \u8bf7\u6c42\u72b6\u6001\uff0c\u672a\u7ed1\u5b9a\u65f6\u4e3a\u7a7a\u6d41
     */
    public Mono<WebRequestContext> currentRequest() {
        return Mono.deferContextual(context -> Mono.justOrEmpty(context.getOrEmpty(REQUEST_CONTEXT_KEY)));
    }

    /**
     * \u8bfb\u53d6\u5f53\u524d Reactor \u4e0a\u4e0b\u6587\u4e2d\u7684\u4e3b\u4f53\u3002
     * @return \u4e3b\u4f53\uff0c\u672a\u7ed1\u5b9a\u65f6\u4e3a\u7a7a\u6d41
     */
    public Mono<Subject> currentSubject() {
        return Mono.deferContextual(context -> Mono.justOrEmpty(context.getOrEmpty(SUBJECT_KEY)));
    }
}
