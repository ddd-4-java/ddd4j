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
 * 可靠投递的跨 Broker 标准消息头。
 *
 * <p>所有生产端都必须写入 {@link #MESSAGE_ID}；消费端据此实现 Inbox 去重。
 */
public final class MQDeliveryHeaders {

    /**
     * 稳定的业务消息标识，不能使用 broker 分配的瞬时投递标识替代。
     */
    public static final String MESSAGE_ID = "ddd4j-message-id";

    private MQDeliveryHeaders() {
    }
}
