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
package io.ddd4j.mq.delivery;

/**
 * 单次 Outbox 调度的汇总结果。
 *
 * @param claimed 已领取数量
 * @param published 已确认发布数量
 * @param rescheduled 已安排重试数量
 * @param dead 预计进入死信数量
 * @param confirmationLost broker 已接收但租约确认丢失数量，后续可能重复投递
 */
public record MQOutboxDispatchResult(int claimed, int published, int rescheduled, int dead,
                                     int confirmationLost) {
}
