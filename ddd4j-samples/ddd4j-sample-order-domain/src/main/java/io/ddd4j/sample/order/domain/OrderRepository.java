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
package io.ddd4j.sample.order.domain;

import java.util.List;
import java.util.Optional;

/**
 * 类型说明。
 * @author ddd4j
 * @since 3.0.x
 */
public interface OrderRepository {

    /**
     * 保存订单聚合根（新增或更新）。
     *
     * @param order 订单聚合根
     */
    void save(Order order);

    /**
     * 按订单标识查询订单。
     *
     * @param orderId 订单标识
     * @return 订单（不存在时返回 {@link Optional#empty()}）
     */
    Optional<Order> findById(String orderId);

    /**
     * 按订单号查询订单。
     *
     * @param orderNo 订单号
     * @return 订单（不存在时返回 {@link Optional#empty()}）
     */
    Optional<Order> findByOrderNo(String orderNo);

    /**
     * 分页查询全部订单。
     *
     * @param offset 起始偏移量
     * @param limit 单页最大记录数
     * @return 订单列表
     */
    List<Order> findAll(int offset, int limit);

    /**
     * 统计订单总数。
     *
     * @return 订单总数
     */
    long count();
}
