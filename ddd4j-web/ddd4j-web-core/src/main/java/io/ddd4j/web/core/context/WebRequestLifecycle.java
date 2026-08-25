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
package io.ddd4j.web.core.context;

import io.ddd4j.web.core.auth.BearerSubjectAuthenticator.Authentication;

import java.util.Objects;
import java.util.Optional;
import io.ddd4j.web.core.auth.AuthenticationMode;
import io.ddd4j.web.core.auth.BearerSubjectAuthenticator;
import io.ddd4j.web.core.auth.WebAccessPolicy;

/**
 * 在具体 Web 框架之外统一执行访问策略与 Subject 认证。
 */
public final class WebRequestLifecycle {

    private final BearerSubjectAuthenticator authenticator;
    private final WebAccessPolicy accessPolicy;

    public WebRequestLifecycle(BearerSubjectAuthenticator authenticator, WebAccessPolicy accessPolicy) {
        this.authenticator = Objects.requireNonNull(authenticator, "authenticator must not be null");
        this.accessPolicy = Objects.requireNonNull(accessPolicy, "accessPolicy must not be null");
    }

    public Optional<Authentication> authenticate(WebRequestContext context) {
        WebRequestContext requestContext = Objects.requireNonNull(context, "context must not be null");
        AuthenticationMode mode = Objects.requireNonNull(accessPolicy.authenticationMode(requestContext),
                "access policy must return an authentication mode");
        return switch (mode) {
            case DISABLED -> Optional.empty();
            case OPTIONAL -> authenticator.authenticateOptional(requestContext.authorization());
            case REQUIRED -> Optional.of(authenticator.authenticateSubject(requestContext.authorization()));
        };
    }
}
