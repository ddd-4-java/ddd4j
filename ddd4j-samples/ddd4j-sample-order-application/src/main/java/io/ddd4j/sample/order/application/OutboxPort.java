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
package io.ddd4j.sample.order.application;

import java.util.List;

/**
 * 类型说明。
 * @author ddd4j
 * @since 3.0.x
 */
public interface OutboxPort {

    /**
     * 将消息追加到 Outbox 存储（与业务写入同一事务提交）。
     *
     * @param messages 待写入的消息列表
     */
    void append(List<OutboxMessage> messages);

    /**
     * 查询尚未发布、可继续投递的待发消息。
     *
     * @param limit 单次返回的最大消息数
     * @return 待发布消息列表
     */
    List<OutboxMessage> pending(int limit);

    /**
     * 将指定消息标记为已发布。
     *
     * @param messageId 消息标识
     */
    void markPublished(String messageId);

    /**
     * 将指定消息标记为发布失败，并记录失败原因。
     *
     * @param messageId 消息标识
     * @param reason 失败原因描述
     */
    void markFailed(String messageId, String reason);
}
