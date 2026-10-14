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
package io.ddd4j.sample.javalin.shiro.order.domain.repository;

import io.ddd4j.core.ddd.repository.Repository;
import io.ddd4j.sample.javalin.shiro.order.domain.model.Order;

import java.util.List;
import java.util.Optional;

/**
 * 订单聚合仓库接口。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
public interface OrderRepository extends Repository<Order, String> {

    /**
     * 按订单号查询订单聚合。
     *
     * @param orderNo 订单号
     * @return 命中的订单，不存在时为空
     */
    Optional<Order> findByOrderNo(String orderNo);

    /**
     * 查询全部订单。
     *
     * @return 订单列表，无数据时为空列表
     */
    List<Order> findAll();
}
