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

/** 订单聚合的存储接口。 */
public interface OrderRepository {
    /** @param order 待保存的订单 */
    void save(Order order);

    /**
     * @param orderId 订单标识
     * @return 找到的订单
     */
    Optional<Order> findById(String orderId);

    /**
     * @param orderNo 订单号
     * @return 找到的订单
     */
    Optional<Order> findByOrderNo(String orderNo);

    /**
     * @param offset 起始偏移量
     * @param limit 最大返回数量
     * @return 订单列表
     */
    List<Order> findAll(int offset, int limit);

    /** @return 订单总数 */
    long count();
}
