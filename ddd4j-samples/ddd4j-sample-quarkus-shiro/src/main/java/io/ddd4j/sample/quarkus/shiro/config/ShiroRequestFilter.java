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
package io.ddd4j.sample.quarkus.shiro.config;

import io.ddd4j.kit.lang.StrKit;
import jakarta.annotation.Priority;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.ext.Provider;
import org.apache.shiro.SecurityUtils;
import org.apache.shiro.subject.Subject;
import org.apache.shiro.util.ThreadContext;

import java.io.IOException;

/**
 * 将 RFC 6750 Bearer Token 作为 Shiro Session ID 恢复为当前请求 Subject。
 */
@Provider
@Priority(Priorities.AUTHENTICATION)
public class ShiroRequestFilter implements ContainerRequestFilter, ContainerResponseFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    @Override
    public void filter(ContainerRequestContext requestContext) throws IOException {
        ThreadContext.unbindSubject();
        String authorization = requestContext.getHeaderString(HttpHeaders.AUTHORIZATION);
        Subject.Builder builder = new Subject.Builder(SecurityUtils.getSecurityManager());
        if (StrKit.isNotEmpty(authorization) && authorization.regionMatches(
                true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length())) {
            String token = authorization.substring(BEARER_PREFIX.length()).trim();
            if (StrKit.isNotEmpty(token)) {
                builder.sessionId(token);
            }
        }
        ThreadContext.bind(builder.buildSubject());
    }

    @Override
    public void filter(ContainerRequestContext requestContext, ContainerResponseContext responseContext) throws IOException {
        ThreadContext.unbindSubject();
    }
}
