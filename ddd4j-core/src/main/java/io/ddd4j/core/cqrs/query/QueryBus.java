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

package io.ddd4j.core.cqrs.query;

/**
 * 框架无关的 CQRS 查询总线（读写分离的只读分发点）。
 *
 * <p>对应规格：{@code complete-cqrs-es-base-capabilities/query-bus}。
 * 仅暴露 {@link #ask(Object)} 只读入口；写路径一律走
 * {@code io.ddd4j.core.cqrs.command.CommandBus}，两侧不得混用。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 1.0.x
 */
public interface QueryBus {

    /**
     * 只读分发：按查询对象类型路由到注册的 {@link QueryHandler} 并返回其结果。
     *
     * <p>查询路径 MUST NOT 产生写副作用；重复 ask 同一查询 MUST 返回一致结果。
     *
     * @param query 查询对象
     * @param <R>   结果类型
     * @return 处理器返回的查询结果
     * @throws IllegalStateException 查询类型未注册处理器（拒绝，不隐式落入写路径）
     */
    <R> R ask(Object query);
}
