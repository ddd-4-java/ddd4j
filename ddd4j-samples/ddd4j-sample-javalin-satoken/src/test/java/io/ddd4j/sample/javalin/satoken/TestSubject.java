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
package io.ddd4j.sample.javalin.satoken;

import java.util.Objects;

import io.ddd4j.auth.satoken.subject.SaTokenSubject;

/**
 * 测试用 Subject：覆盖 {@link #getUserId()}，从 principal 取值而非走 sa-token extra 通道。
 *
 * <p>业务代码（{@code AuthController}）调用 {@code SubjectKit.getUserId()}，sa-token 默认实现
 * 走 {@code StpKit.getUserId()} → {@code StpUtil.getExtra("uid")}，未集成 sa-token-jwt 时抛
 * {@code ApiDisabledException}。本测试侧通过覆盖 getUserId 回退到 ddd4j 默认从
 * {@link io.ddd4j.core.auth.AuthPrincipal} 读取的方式，无需修改任何业务代码。
 */
public class TestSubject extends SaTokenSubject {

    @Override
    public Object getUserId() {
        // 回退到 Subject 接口默认实现：从 principal.getUserId() 取值
        var principal = getPrincipal();
        return Objects.nonNull(principal) ? principal.getUserId() : null;
    }
}