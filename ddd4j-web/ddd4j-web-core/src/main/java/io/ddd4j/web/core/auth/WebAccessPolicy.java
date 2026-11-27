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
     * \u8fd4\u56de\u4e0d\u8981\u6c42\u8ba4\u8bc1\u7684\u8bbf\u95ee\u7b56\u7565\u3002
     * @return \u514d\u8ba4\u8bc1\u7b56\u7565
     */
    static WebAccessPolicy disabled() {
        return context -> AuthenticationMode.DISABLED;
    }

    /**
     * \u8fd4\u56de\u53ef\u9009\u8ba4\u8bc1\u7684\u8bbf\u95ee\u7b56\u7565\u3002
     * @return \u53ef\u9009\u8ba4\u8bc1\u7b56\u7565
     */
    static WebAccessPolicy optional() {
        return context -> AuthenticationMode.OPTIONAL;
    }

    /**
     * \u8fd4\u56de\u5fc5\u987b\u8ba4\u8bc1\u7684\u8bbf\u95ee\u7b56\u7565\u3002
     * @return \u5fc5\u987b\u8ba4\u8bc1\u7b56\u7565
     */
    static WebAccessPolicy required() {
        return context -> AuthenticationMode.REQUIRED;
    }

    /**
     * \u9664\u516c\u5f00\u8def\u5f84\u5916\u90fd\u8981\u6c42\u8ba4\u8bc1\u3002
     * @param publicPath \u516c\u5f00\u8def\u5f84\u5224\u65ad\u5668
     * @return \u8def\u5f84\u8bbf\u95ee\u7b56\u7565
     */
    static WebAccessPolicy requiredExcept(Predicate<String> publicPath) {
        Predicate<String> pathPredicate = Objects.requireNonNull(publicPath, "publicPath must not be null");
        return context -> pathPredicate.test(context.path())
                ? AuthenticationMode.DISABLED : AuthenticationMode.REQUIRED;
    }

    /**
     * \u51b3\u5b9a\u5f53\u524d\u8bf7\u6c42\u7684\u8ba4\u8bc1\u6a21\u5f0f\u3002
     * @param context \u8bf7\u6c42\u4e0a\u4e0b\u6587
     * @return \u8ba4\u8bc1\u6a21\u5f0f
     */
    AuthenticationMode authenticationMode(WebRequestContext context);
}
