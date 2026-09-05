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
package io.ddd4j.sample.quarkus.cqrs.order.domain.repository;

import io.ddd4j.core.ddd.repository.Repository;
import io.ddd4j.sample.quarkus.cqrs.order.domain.model.Order;

import java.util.List;
import java.util.Optional;

/**
 * 订单聚合仓库接口。
 *
 * <p>继承自 ddd4j-core 的 {@link Repository}，业务方可按需扩展查询方法。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
public interface OrderRepository extends Repository<Order, String> {

    /**
     * 根据订单编号查询订单。
     *
     * @param orderNo 订单编号
     * @return 查询结果
     */
    Optional<Order> findByOrderNo(String orderNo);

    /**
     * 列出全部订单（CQRS 缓存统计用）。
     *
     * @return 全部订单聚合列表
     */
    List<Order> findAll();
}