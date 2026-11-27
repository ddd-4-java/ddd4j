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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.ddd4j.core.context.ThreadContext;
import io.ddd4j.core.subject.Subject;
import io.ddd4j.web.core.context.WebContextScope;
import io.ddd4j.web.core.context.WebRequestContext;
import io.micronaut.http.context.ServerRequestContext;

import java.util.Objects;
import java.util.Optional;

/**
 * 通过 Micronaut 3 请求上下文与 Reactor 调用插桩传播 ddd4j 请求状态。
 */
public final class Ddd4jMicronautContext {

    /** 保存 <code>Ddd4jMicronautContext.class.getName()</code>，供 <code>Ddd4jMicronautContext</code> 的处理流程复用。 */
    static final String REQUEST_ATTRIBUTE = Ddd4jMicronautContext.class.getName();

    /** 保存 <code>CURRENT</code> 对应的 <code>ThreadLocal&lt;Ddd4jMicronautContext&gt;</code> 数据。 */
    private static final ThreadLocal<Ddd4jMicronautContext> CURRENT = new ThreadLocal<>();

    /** \u5f53\u524d HTTP \u8bf7\u6c42\u89e3\u6790\u540e\u7528\u4e8e\u8de8\u5c42\u4f20\u9012\u7684\u4e0a\u4e0b\u6587\u3002 */
    private final WebRequestContext requestContext;
    /** \u5f53\u524d\u8bf7\u6c42\u6240\u7ed1\u5b9a\u7684\u4e3b\u4f53\u64cd\u4f5c\u5165\u53e3\u3002 */
    private final Optional<Subject> subject;

    /**
     * 创建请求状态。
     *
     * @param requestContext Web 请求上下文
     * @param subject 当前主体，可为空
     */
    @JsonCreator
    public Ddd4jMicronautContext(@JsonProperty("requestContext") WebRequestContext requestContext,
                                  @JsonProperty("subject") Optional<Subject> subject) {
        this.requestContext = Objects.requireNonNull(requestContext, "requestContext must not be null");
        this.subject = Objects.requireNonNull(subject, "subject must not be null");
    }

    /**
     * \u83b7\u53d6\u5f53\u524d Micronaut \u4f20\u64ad\u4e0a\u4e0b\u6587\u4e2d\u7684\u8bf7\u6c42\u4e0a\u4e0b\u6587\u3002
     * @return \u5f53\u524d\u8bf7\u6c42\u4e0a\u4e0b\u6587\uff0c\u53ef\u4e3a\u7a7a
     */
    public static Optional<Ddd4jMicronautContext> current() {
        Ddd4jMicronautContext current = CURRENT.get();
        if (Objects.nonNull(current)) {
            return Optional.of(current);
        }
        return ServerRequestContext.currentRequest()
                .flatMap(request -> request.getAttribute(REQUEST_ATTRIBUTE, Ddd4jMicronautContext.class));
    }

    /**
     * 执行 <code>set</code>，调用 <code>CURRENT.set</code>。
     *
     * @param context 传入此操作的 <code>Ddd4jMicronautContext</code> 类型 <code>context</code>
     */
    public static void set(Ddd4jMicronautContext context) {
        CURRENT.set(context);
    }

    /** 执行 <code>clear</code>，调用 <code>CURRENT.remove</code>。 */
    public static void clear() {
        CURRENT.remove();
    }

    /**
     * 执行 <code>current Thread Context</code>，调用 <code>CURRENT.get</code>。
     *
     * @return 返回表达式 <code>CURRENT.get()</code>
     */
    static Ddd4jMicronautContext currentThreadContext() {
        return CURRENT.get();
    }

    /**
     * 执行 <code>restore</code> 对应的处理。
     *
     * @param previous 传入此操作的 <code>Ddd4jMicronautContext</code> 类型 <code>previous</code>
     */
    static void restore(Ddd4jMicronautContext previous) {
        if (Objects.isNull(previous)) {
            clear();
        } else {
            set(previous);
        }
    }

    /**
     * \u83b7\u53d6 Web \u8bf7\u6c42\u4e0a\u4e0b\u6587\u3002
     * @return Web \u8bf7\u6c42\u4e0a\u4e0b\u6587
     */
    @JsonProperty("requestContext")
    public WebRequestContext requestContext() {
        return requestContext;
    }

    /**
     * \u83b7\u53d6\u5f53\u524d\u4e3b\u4f53\u3002
     * @return \u53ef\u9009\u4e3b\u4f53
     */
    @JsonProperty("subject")
    public Optional<Subject> subject() {
        return subject;
    }

    /**
     * 读取<code>scope</code> 的当前值。
     *
     * @return 返回表达式 <code>scope</code>
     */
    public WebContextScope openContext() {
        WebContextScope scope = WebContextScope.open(requestContext);
        subject.ifPresent(ThreadContext::bind);
        return scope;
    }

    /**
     * \u83b7\u53d6 Web \u8bf7\u6c42\u4e0a\u4e0b\u6587\u7684 JavaBean \u8bbf\u95ee\u65b9\u6cd5\u3002
     * @return Web \u8bf7\u6c42\u4e0a\u4e0b\u6587
     */
    public WebRequestContext getRequestContext() {
        return requestContext;
    }

    /**
     * \u83b7\u53d6\u5f53\u524d\u4e3b\u4f53\u7684 JavaBean \u8bbf\u95ee\u65b9\u6cd5\u3002
     * @return \u53ef\u9009\u4e3b\u4f53
     */
    public Optional<Subject> getSubject() {
        return subject;
    }

    /** {@inheritDoc} */
    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (Objects.isNull(obj) || getClass() != obj.getClass()) {
            return false;
        }
        Ddd4jMicronautContext other = (Ddd4jMicronautContext) obj;
        return Objects.equals(requestContext, other.requestContext)
                && Objects.equals(subject, other.subject);
    }

    /** {@inheritDoc} */
    @Override
    public int hashCode() {
        int result = Objects.hashCode(requestContext);
        result = 31 * result + Objects.hashCode(subject);
        return result;
    }

    /** {@inheritDoc} */
    @Override
    public String toString() {
        return "Ddd4jMicronautContext[requestContext=" + requestContext + ", subject=" + subject + "]";
    }
}
