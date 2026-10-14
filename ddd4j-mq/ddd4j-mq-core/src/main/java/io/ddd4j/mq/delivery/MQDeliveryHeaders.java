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
 *
 * <p>因果元数据 header（{@link #CORRELATION_ID}/{@link #CAUSATION_ID}）是对外冻结契约：
 * header 为权威来源，payload 内嵌字段为迁移期镜像（对应 OpenSpec change
 * {@code promote-causality-to-broker-headers}，Requirement: Causality in broker headers）。
 */
public final class MQDeliveryHeaders {

    /**
     * 稳定的业务消息标识，不能使用 broker 分配的瞬时投递标识替代。
     */
    public static final String MESSAGE_ID = "ddd4j-message-id";

    /**
     * 因果链关联 ID broker header（冻结命名，header 权威值）。
     *
     * <p>任意语言的消费方可直接从 broker headers 提取，无需解析 payload。
     */
    public static final String CORRELATION_ID = "X-Correlation-Id";

    /**
     * 因果链因果 ID broker header（冻结命名，header 权威值）。
     *
     * <p>任意语言的消费方可直接从 broker headers 提取，无需解析 payload。
     */
    public static final String CAUSATION_ID = "X-Causation-Id";

    private MQDeliveryHeaders() {
    }
}
