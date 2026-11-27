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
import io.micronaut.core.propagation.PropagatedContext;
import io.micronaut.core.propagation.ThreadPropagatedContextElement;

import java.io.InvalidObjectException;
import java.io.ObjectStreamException;
import java.util.Objects;
import java.util.Optional;

/**
 * 通过 Micronaut PropagatedContext 传播 ddd4j 请求上下文。
 */
public final class Ddd4jMicronautContext implements ThreadPropagatedContextElement<WebContextScope> {

    /** Java \u5e8f\u5217\u5316\u7248\u672c\u6807\u8bc6\u3002 */
    private static final long serialVersionUID = 0L;

    /** \u5f53\u524d HTTP \u8bf7\u6c42\u89e3\u6790\u540e\u7528\u4e8e\u8de8\u5c42\u4f20\u9012\u7684\u4e0a\u4e0b\u6587\u3002 */
    private final WebRequestContext requestContext;

    /** \u5f53\u524d\u8bf7\u6c42\u6240\u7ed1\u5b9a\u7684\u4e3b\u4f53\u64cd\u4f5c\u5165\u53e3\u3002 */
    private final Optional<Subject> subject;

    /**
     * 构造可传播的请求上下文。
     *
     * @param requestContext Web 请求上下文
     * @param subject        当前主体，可为空
     */
    @JsonCreator()
    public Ddd4jMicronautContext(@JsonProperty("requestContext") WebRequestContext requestContext, @JsonProperty("subject") Optional<Subject> subject) {
        Objects.requireNonNull(requestContext, "requestContext must not be null");
        subject = Objects.requireNonNull(subject, "subject must not be null");
        this.requestContext = requestContext;
        this.subject = subject;
    }

    /**
     * 获取当前 Micronaut 传播上下文中的请求上下文。
     *
     * @return 当前请求上下文，可为空
     */
    public static Optional<Ddd4jMicronautContext> current() {
        return PropagatedContext.getOrEmpty().find(Ddd4jMicronautContext.class);
    }

    /**
     * \u628a\u5f53\u524d Micronaut \u8bf7\u6c42\u4e0a\u4e0b\u6587\u4f20\u64ad\u5230\u5de5\u4f5c\u7ebf\u7a0b\u3002
     * @return \u6062\u590d\u65e7\u7ebf\u7a0b\u72b6\u6001\u6240\u9700\u7684\u4f5c\u7528\u57df
     */
    @Override
    public WebContextScope updateThreadContext() {
        WebContextScope scope = WebContextScope.open(requestContext);
        subject.ifPresent(ThreadContext::bind);
        return scope;
    }

    /**
     * \u6062\u590d\u4f20\u64ad\u5f53\u524d\u8bf7\u6c42\u4e4b\u524d\u7684\u7ebf\u7a0b\u72b6\u6001\u3002
     * @param oldState \u5efa\u7acb\u8bf7\u6c42\u7ed1\u5b9a\u524d\u4fdd\u5b58\u7684\u7ebf\u7a0b\u4f5c\u7528\u57df
     */
    @Override
    public void restoreThreadContext(WebContextScope oldState) {
        oldState.close();
    }

    /**
     * 获取 Web 请求上下文。
     *
     * @return Web 请求上下文
     */
    @JsonProperty("requestContext")
    public WebRequestContext requestContext() {
        return requestContext;
    }

    /**
     * 获取当前主体。
     *
     * @return 可选主体
     */
    @JsonProperty("subject")
    public Optional<Subject> subject() {
        return subject;
    }

    /**
     * 获取 Web 请求上下文的 JavaBean 访问方法。
     *
     * @return Web 请求上下文
     */
    public WebRequestContext getRequestContext() {
        return requestContext;
    }

    /**
     * 获取当前主体的 JavaBean 访问方法。
     *
     * @return 可选主体
     */
    public Optional<Subject> getSubject() {
        return subject;
    }

    /**
     * \u53cd\u5e8f\u5217\u5316\u540e\u91cd\u5efa\u5b8c\u6574\u7684\u8ba4\u8bc1\u8bf7\u6c42\u4e0a\u4e0b\u6587\u5bf9\u8c61\u3002
     * @return \u91cd\u5efa\u5e76\u7ecf\u8fc7\u6821\u9a8c\u7684 Micronaut \u8bf7\u6c42\u4e0a\u4e0b\u6587
     * @throws ObjectStreamException \u53cd\u5e8f\u5217\u5316\u6570\u636e\u4e0d\u80fd\u5f62\u6210\u6709\u6548\u7684\u8bf7\u6c42\u4e0a\u4e0b\u6587
     */
    private Object readResolve() throws ObjectStreamException {
        try {
            return new Ddd4jMicronautContext(requestContext, subject);
        } catch (RuntimeException cause) {
            InvalidObjectException failure = new InvalidObjectException(cause.getMessage());
            failure.initCause(cause);
            throw failure;
        }
    }

    /**
     * 根据<code>requestContext</code>、<code>subject</code>比较对象的值是否相等。
     *
     * <p>只比较运行时类型相同的对象。</p>
     *
     * @param obj 用来比较或转换的输入对象
     * @return 对象满足此实现的相等条件时为真
     */
    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (Objects.isNull(obj) || getClass() != obj.getClass()) {
            return false;
        }
        Ddd4jMicronautContext other = (Ddd4jMicronautContext) obj;
        return Objects.equals(this.requestContext, other.requestContext) && Objects.equals(this.subject, other.subject);
    }

    /**
     * 计算与对象相等条件一致的哈希值。
     *
     * @return 由参与相等比较的状态计算出的哈希值
     */
    @Override
    public int hashCode() {
        int result = 0;
        result = 31 * result + Objects.hashCode(requestContext);
        result = 31 * result + Objects.hashCode(subject);
        return result;
    }

    /**
     * 生成用于调试和日志的对象描述，包含<code>requestContext</code>、<code>subject</code>。
     *
     * @return 表示当前对象状态的字符串
     */
    @Override
    public String toString() {
        return "Ddd4jMicronautContext[requestContext=" + requestContext + ", subject=" + subject + "]";
    }
}
