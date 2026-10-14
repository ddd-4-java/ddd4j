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

import com.fasterxml.jackson.annotation.JsonProperty;

import com.fasterxml.jackson.annotation.JsonCreator;

import java.io.InvalidObjectException;

import java.io.ObjectStreamException;

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
public final class Ddd4jMicronautContext implements ThreadPropagatedContextElement<WebContextScope> {

    private static final long serialVersionUID = 0L;

    private final WebRequestContext requestContext;

    private final Optional<Subject> subject;

    @JsonCreator()
    public Ddd4jMicronautContext(@JsonProperty("requestContext") WebRequestContext requestContext, @JsonProperty("subject") Optional<Subject> subject) {
        Objects.requireNonNull(requestContext, "requestContext must not be null");
        subject = Objects.requireNonNull(subject, "subject must not be null");
        this.requestContext = requestContext;
        this.subject = subject;
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

    @JsonProperty("requestContext")
    public WebRequestContext requestContext() {
        return requestContext;
    }

    @JsonProperty("subject")
    public Optional<Subject> subject() {
        return subject;
    }

    private Object readResolve() throws ObjectStreamException {
        try {
            return new Ddd4jMicronautContext(requestContext, subject);
        } catch (RuntimeException cause) {
            InvalidObjectException failure = new InvalidObjectException(cause.getMessage());
            failure.initCause(cause);
            throw failure;
        }
    }

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

    @Override
    public int hashCode() {
        int result = 0;
        result = 31 * result + Objects.hashCode(requestContext);
        result = 31 * result + Objects.hashCode(subject);
        return result;
    }

    @Override
    public String toString() {
        return "Ddd4jMicronautContext[requestContext=" + requestContext + ", subject=" + subject + "]";
    }
}
