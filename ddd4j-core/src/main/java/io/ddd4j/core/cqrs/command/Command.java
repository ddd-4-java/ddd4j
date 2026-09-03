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
package io.ddd4j.core.cqrs.command;

/**
 * CQRS 命令标记接口（写侧）。
 * <p>
 * 取代 {@code io.ddd4j.core.ddd.command.DddAggregateCommand} 的 marker 接口语义。
 *
 * <h3>命令设计原则</h3>
 * <ul>
 *   <li><b>不可变</b>：命令对象一旦创建不应修改</li>
 *   <li><b>意图载体</b>：命令表达"用户想做什么"，而非"如何做"</li>
 *   <li><b>业务方法命名</b>：动词过去式（{@code CreateOrder} / {@code CancelOrder}）</li>
 *   <li><b>可序列化</b>：可跨进程传输（同步 HTTP / 异步 MQ）</li>
 * </ul>
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * public class CreateOrderCommand implements Command {
 *     private final OrderId orderId;
 *     private final Money total;
 *     private final List<OrderItem> items;
 *
 *     public CreateOrderCommand(OrderId orderId, Money total, List<OrderItem> items) {
 *         this.orderId = orderId;
 *         this.total = total;
 *         this.items = List.copyOf(items);
 *     }
 *
 *     // getters
 * }
 *
 * &#64;ApplicationService
 * public class CreateOrderCmdExe implements CommandExecutor<CreateOrderCommand> {
 *     &#64;Override
 *     public Result execute(CreateOrderCommand cmd) {
 *         Order order = new Order(cmd.getOrderId(), cmd.getTotal());
 *         orderRepository.save(order);
 *         return Result.ok();
 *     }
 * }
 * }</pre>
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 3.0.0
 */
public interface Command {
}