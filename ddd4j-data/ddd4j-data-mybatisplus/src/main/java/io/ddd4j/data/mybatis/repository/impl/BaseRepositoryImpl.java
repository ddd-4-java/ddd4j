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

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.ddd4j.core.cqrs.query.Query;
import io.ddd4j.core.ddd.model.AggregateRoot;
import io.ddd4j.data.mybatis.repository.MybatisAggregateRepository;

import java.io.Serializable;

/**
 * 业务仓储的统一继承入口，公共实现由 {@link MybatisAggregateRepository} 提供。
 *
 * <p>泛型解析、领域 CRUD、批量操作和查询注册均继承父类，不再重复覆盖。
 * 原生 PO 操作直接使用 {@link #getBaseMapper()}；批量事务遵循组件 API 的行为。
 *
 * <pre>{@code
 * public class OrderRepositoryImpl
 *         extends BaseRepositoryImpl<OrderMapper, Order, OrderPO, OrderQuery, Long>
 *         implements OrderRepository {
 * }
 * }</pre>
 *
 * @param <MP> MyBatis-Plus Mapper 类型
 * @param <M> 聚合根类型，主键类型必须与 ID 相同
 * @param <P> 持久化对象类型
 * @param <Q> 领域查询类型
 * @param <ID> 聚合根标识类型
 */
public abstract class BaseRepositoryImpl<MP extends BaseMapper<P>, M extends AggregateRoot<ID>, P, Q extends Query<M>, ID extends Serializable>
        extends MybatisAggregateRepository<MP, M, P, Q, ID> {

}
