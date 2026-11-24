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

import io.ddd4j.sample.order.application.OrderReadModel;
import io.ddd4j.sample.order.application.OutboxMessage;
import java.util.List;
import java.util.Optional;

/** CQRS 场景的观测和故障注入边界；查询必须读取独立投影，不能回退写模型。 */
public interface CqrsOrderSampleDriver extends OrderSampleDriver {
    /**
     * 直接查询写模型存储。
     * @param orderId 订单标识
     * @return 从实际写仓储读取的快照
     */
    OrderReadModel persisted(String orderId);
    /**
     * 读取命令产生的持久事件。
     * @param orderId 订单标识
     * @return 实际持久事件，不消费事件
     */
    List<OutboxMessage> events(String orderId);
    /**
     * 直接查询独立投影存储。
     * @param orderId 订单标识
     * @return 独立投影结果，未处理时为空
     */
    Optional<OrderReadModel> projection(String orderId);
    /**
     * 读取投影持久检查点。
     * @return 实际持久化的下一源事件位置，不得用事件数量代替
     */
    long position();
    /** 使下一次投影业务处理抛出 IllegalStateException，失败发生在写入查询模型之前。 */
    void failNextProjection();
    /** 执行并等待一个实际投影批次；业务失败保留 IllegalStateException，不得吞掉异常。 */
    void project();
}
