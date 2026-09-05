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
 * Outbox 消息的可持久化生命周期状态。
 */
public enum MQOutboxStatus {

    /** 等待领取或下一次重试。 */
    PENDING,
    /** 已被某个发布实例短期租约领取。 */
    LEASED,
    /** 已由 broker 确认接收。 */
    PUBLISHED,
    /** 重试次数耗尽，等待人工诊断或重放。 */
    DEAD
}
