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
 * CQRS 查询处理器：声明可处理的查询类型并执行只读取数。
 *
 * <p>对应规格：{@code complete-cqrs-es-base-capabilities/query-bus}。
 * 处理器 MUST 保持只读语义——仅读取读模型，不产生写副作用。
 *
 * @param <Q> 查询类型
 * @param <R> 查询结果类型
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 1.0.x
 */
public interface QueryHandler<Q, R> {

    /**
     * 返回本处理器支持的查询类型。
     *
     * @return 查询类型（作为路由键）
     */
    Class<Q> queryType();

    /**
     * 执行只读查询。
     *
     * @param query 查询对象
     * @return 查询结果
     */
    R handle(Q query);
}
