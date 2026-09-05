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
 * 将已领取 Outbox 记录发送到底层 broker 的端口。
 */
@FunctionalInterface
public interface MQOutboxSender {

    /**
     * 发送一条已领取消息。
     *
     * @param record 已携带稳定消息头的 Outbox 记录
     */
    void send(MQOutboxRecord record);
}
