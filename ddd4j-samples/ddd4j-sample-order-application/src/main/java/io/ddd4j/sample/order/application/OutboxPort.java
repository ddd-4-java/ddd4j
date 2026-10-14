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

/** 待发布消息的持久化端口。 */
public interface OutboxPort {
    /** @param messages 待追加的消息 */
    void append(List<OutboxMessage> messages);

    /**
     * @param limit 最大返回数量
     * @return 待发布消息
     */
    List<OutboxMessage> pending(int limit);

    /** @param messageId 已成功发布的消息标识 */
    void markPublished(String messageId);

    /**
     * @param messageId 发布失败的消息标识
     * @param reason 失败原因
     */
    void markFailed(String messageId, String reason);
}
