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
package io.ddd4j.sample.order.testkit;

import io.ddd4j.sample.order.application.AddOrderLineCommand;
import io.ddd4j.sample.order.application.CreateOrderCommand;
import io.ddd4j.sample.order.application.OrderReadModel;
import io.ddd4j.sample.order.domain.OrderQuery;
import java.util.List;

/** 默认订单场景的业务边界；平台实现必须调用实际 HTTP/容器入口并解码实际响应。 */
public interface OrderSampleDriver {
    /**
     * 创建并保存草稿订单。
     * @param command 创建输入
     * @return 实际创建的订单标识
     */
    String create(CreateOrderCommand command);
    /**
     * 添加订单明细并保存更新。
     * @param command 明细输入
     */
    void addLine(AddOrderLineCommand command);
    /**
     * 使用幂等键支付订单。
     * @param orderId 订单标识
     * @param paymentKey 支付幂等键
     */
    void pay(String orderId, String paymentKey);
    /**
     * 按标识查询订单。
     * @param orderId 订单标识
     * @return 实际查询结果
     */
    OrderReadModel find(String orderId);
    /**
     * 执行订单分页过滤查询。
     * @param query 查询条件
     * @return 实际分页查询结果
     */
    List<OrderReadModel> query(OrderQuery query);
}
