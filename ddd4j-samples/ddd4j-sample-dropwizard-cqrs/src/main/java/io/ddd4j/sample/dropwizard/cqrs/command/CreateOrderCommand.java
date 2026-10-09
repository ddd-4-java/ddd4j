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
package io.ddd4j.sample.dropwizard.cqrs.command;

import io.ddd4j.core.cqrs.command.Command;

/**
 * 创建订单命令（CQRS 写侧）。
 *
 * @param orderNo   订单编号
 * @param buyerId   买家 ID
 * @param buyerName 买家名称
 */
public record CreateOrderCommand(String orderNo, String buyerId, String buyerName) implements Command {
}
