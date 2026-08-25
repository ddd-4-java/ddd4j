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
package io.ddd4j.web.dropwizard;

import io.ddd4j.web.core.context.SynchronousWebRequestSession;
import io.ddd4j.web.core.context.WebHeaders;
import io.ddd4j.web.core.context.WebRequestContext;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;

/**
 * 回传请求标识，并按响应状态提交或释放幂等请求会话。
 */
public final class Ddd4jDropwizardResponseFilter implements ContainerResponseFilter {

    @Override
    public void filter(ContainerRequestContext request, ContainerResponseContext response) {
        Object contextValue = request.getProperty(Ddd4jDropwizardRequestFilter.CONTEXT_PROPERTY);
        if (contextValue instanceof WebRequestContext context) {
            response.getHeaders().putSingle(WebHeaders.REQUEST_ID, context.requestId());
            response.getHeaders().putSingle(WebHeaders.TRACE_ID, context.traceId());
        }
        Object sessionValue = request.getProperty(Ddd4jDropwizardRequestFilter.SESSION_PROPERTY);
        if (sessionValue instanceof SynchronousWebRequestSession session) {
            session.complete(response.getStatus() < 400);
        }
        request.removeProperty(Ddd4jDropwizardRequestFilter.CONTEXT_PROPERTY);
        request.removeProperty(Ddd4jDropwizardRequestFilter.SESSION_PROPERTY);
    }
}
