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

    /**
     * Java 序列化版本标识。
     */
    private static final long serialVersionUID = 0L;

    /**
     * 当前 HTTP 请求解析后用于跨层传递的上下文。
     */
    private final WebRequestContext requestContext;

    /**
     * 当前请求所绑定的主体操作入口。
     */
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
     * 把当前 Micronaut 请求上下文传播到工作线程。
     *
     * @return 恢复旧线程状态所需的作用域
     */
    @Override
    public WebContextScope updateThreadContext() {
        WebContextScope scope = WebContextScope.open(requestContext);
        subject.ifPresent(ThreadContext::bind);
        return scope;
    }

    /**
     * 恢复传播当前请求之前的线程状态。
     *
     * @param oldState 建立请求绑定前保存的线程作用域
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
     * 反序列化后重建完整的认证请求上下文对象。
     *
     * @return 重建并经过校验的 Micronaut 请求上下文
     * @throws ObjectStreamException 反序列化数据不能形成有效的请求上下文
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
