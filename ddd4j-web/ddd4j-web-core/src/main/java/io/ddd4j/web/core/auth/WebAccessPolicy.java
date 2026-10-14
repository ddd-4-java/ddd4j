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

import io.ddd4j.web.core.context.WebRequestContext;

import java.util.Objects;
import java.util.function.Predicate;

/**
 * 决定一次 HTTP 请求采用何种认证模式。
 */
@FunctionalInterface
public interface WebAccessPolicy {

    /**
     * 返回不要求认证的访问策略。
     *
     * @return 免认证策略
     */
    static WebAccessPolicy disabled() {
        return context -> AuthenticationMode.DISABLED;
    }

    /**
     * 返回可选认证的访问策略。
     *
     * @return 可选认证策略
     */
    static WebAccessPolicy optional() {
        return context -> AuthenticationMode.OPTIONAL;
    }

    /**
     * 返回必须认证的访问策略。
     *
     * @return 必须认证策略
     */
    static WebAccessPolicy required() {
        return context -> AuthenticationMode.REQUIRED;
    }

    /**
     * 除公开路径外都要求认证。
     *
     * @param publicPath 公开路径判断器
     * @return 路径访问策略
     */
    static WebAccessPolicy requiredExcept(Predicate<String> publicPath) {
        Predicate<String> pathPredicate = Objects.requireNonNull(publicPath, "publicPath must not be null");
        return context -> pathPredicate.test(context.path())
                ? AuthenticationMode.DISABLED : AuthenticationMode.REQUIRED;
    }

    /**
     * 决定当前请求的认证模式。
     *
     * @param context 请求上下文
     * @return 认证模式
     */
    AuthenticationMode authenticationMode(WebRequestContext context);
}
