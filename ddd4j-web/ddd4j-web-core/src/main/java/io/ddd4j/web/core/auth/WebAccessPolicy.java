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
package io.ddd4j.web.core.auth;

import java.util.Objects;
import java.util.function.Predicate;
import io.ddd4j.web.core.context.WebRequestContext;

/**
 * 决定一次 HTTP 请求采用何种认证模式。
 */
@FunctionalInterface
public interface WebAccessPolicy {

    AuthenticationMode authenticationMode(WebRequestContext context);

    static WebAccessPolicy disabled() {
        return context -> AuthenticationMode.DISABLED;
    }

    static WebAccessPolicy optional() {
        return context -> AuthenticationMode.OPTIONAL;
    }

    static WebAccessPolicy required() {
        return context -> AuthenticationMode.REQUIRED;
    }

    static WebAccessPolicy requiredExcept(Predicate<String> publicPath) {
        Predicate<String> pathPredicate = Objects.requireNonNull(publicPath, "publicPath must not be null");
        return context -> pathPredicate.test(context.path())
                ? AuthenticationMode.DISABLED : AuthenticationMode.REQUIRED;
    }
}
