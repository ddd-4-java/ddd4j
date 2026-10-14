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
     * 创建禁用认证的策略：不解析也不校验认证信息。
     *
     * @return 认证模式为 {@code DISABLED} 的策略
     */
    static WebAccessPolicy disabled() {
        return context -> AuthenticationMode.DISABLED;
    }

    /**
     * 创建可选认证的策略：令牌可缺省，但携带后必须有效。
     *
     * @return 认证模式为 {@code OPTIONAL} 的策略
     */
    static WebAccessPolicy optional() {
        return context -> AuthenticationMode.OPTIONAL;
    }

    /**
     * 创建强制认证的策略：必须携带有效的令牌。
     *
     * @return 认证模式为 {@code REQUIRED} 的策略
     */
    static WebAccessPolicy required() {
        return context -> AuthenticationMode.REQUIRED;
    }

    /**
     * 创建按路径豁免的强制认证策略：命中公开路径谓词的请求禁用认证，其余请求强制认证。
     *
     * @param publicPath 判断路径是否为公开路径的谓词，不可为空
     * @return 按路径豁免的认证策略
     * @throws NullPointerException 谓词为空时抛出
     */
    static WebAccessPolicy requiredExcept(Predicate<String> publicPath) {
        Predicate<String> pathPredicate = Objects.requireNonNull(publicPath, "publicPath must not be null");
        return context -> pathPredicate.test(context.path())
                ? AuthenticationMode.DISABLED : AuthenticationMode.REQUIRED;
    }

    /**
     * 判定当前请求应采用的认证模式。
     *
     * @param context 当前 Web 请求上下文
     * @return 该请求适用的认证模式
     */
    AuthenticationMode authenticationMode(WebRequestContext context);
}
