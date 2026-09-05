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

import java.time.Instant;

/**
 * 消费端 Inbox 去重端口。
 *
 * <p>实现必须以 {@code consumerId + messageId} 作为唯一键，并与消费者业务写操作处于同一事务边界：
 * 成功写入代表本次消息可安全 ACK，业务失败时事务必须回滚该写入以便重试。
 */
public interface MQInboxStore {

    /**
     * 记录一条尚未处理的消息。
     *
     * @param consumerId 稳定消费者标识
     * @param messageId 生产端传入的稳定消息标识
     * @param processedAt 处理开始时间
     * @return {@code true} 表示首次处理；{@code false} 表示重复消息，应直接 ACK
     */
    boolean recordIfAbsent(String consumerId, String messageId, Instant processedAt);
}
