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
package io.ddd4j.sample.order.application;

import io.ddd4j.sample.order.domain.OrderQuery;

import java.util.List;
import java.util.Optional;

/**
 * 类型说明。
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 3.0.x
 */
public interface OrderReadModelPort {

    /**
     * 将订单读模型投影写入读侧存储（覆盖或新增）。
     *
     * @param order 订单读模型
     */
    void project(OrderReadModel order);

    /**
     * 按订单标识查询订单读模型。
     *
     * @param orderId 订单标识
     * @return 订单读模型（不存在时返回 {@link Optional#empty()}）
     */
    Optional<OrderReadModel> findProjectionById(String orderId);

    /**
     * 按条件分页查询订单读模型列表。
     *
     * @param query 查询条件（买方、状态、分页参数）
     * @return 满足条件的订单读模型列表
     */
    List<OrderReadModel> query(OrderQuery query);
}
