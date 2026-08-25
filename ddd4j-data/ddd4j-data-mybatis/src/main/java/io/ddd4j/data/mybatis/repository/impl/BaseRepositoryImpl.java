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
package io.ddd4j.data.mybatis.repository.impl;

import io.ddd4j.data.mybatis.repository.MybatisAggregateRepository;
import io.ddd4j.data.mybatis.mapper.Ddd4jMapper;

/**
 * 业务方入口基类（对齐 mybatisplus 模块的 BaseRepositoryImpl）。
 *
 * <p>子类只需声明五泛型并提供无参构造器供 DI 容器使用：</p>
 * <pre>{@code
 * public class OrderRepository
 *         extends BaseRepositoryImpl<OrderMapper, Order, OrderPO, OrderQuery, Long> {
 * }
 * }</pre>
 *
 * @param <MP> Mapper 类型（须继承 {@link Ddd4jMapper}）
 * @param <M>  聚合根类型
 * @param <P>  持久化对象类型
 * @param <Q>  充血查询类型
 * @param <ID> 标识类型
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 4.0.0
 */
public abstract class BaseRepositoryImpl<
        MP extends Ddd4jMapper<P>,
        M extends io.ddd4j.core.ddd.model.AggregateRoot<?>,
        P,
        Q extends io.ddd4j.core.cqrs.query.Query<M>,
        ID extends java.io.Serializable>
        extends MybatisAggregateRepository<MP, M, P, Q, ID> {
}
