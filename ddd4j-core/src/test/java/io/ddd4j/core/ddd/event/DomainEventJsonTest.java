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
package io.ddd4j.core.ddd.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 通过 <code>event.setAggregateVersion()</code>、<code>objectMapper.writeValueAsString()</code> 验证 <code>DomainEventJson</code> 的运行行为。
 */
class DomainEventJsonTest {

    // Jackson 2 不会自动注册 java.time 支持；findAndAddModules 经 SPI 发现 classpath 上的 jsr310（JavaTimeModule）
    /**
     * 用于读写 JSON 数据的 Jackson 映射器。
     */
    private final ObjectMapper objectMapper = JsonMapper.builder().findAndAddModules().build();

    /**
     * 在测试框架中执行 <code>event.setAggregateVersion()</code>、<code>objectMapper.writeValueAsString()</code>，验证该场景。
     *
     * @throws Exception 处理测试数据的序列化时失败
     */
    @Test
    void shouldSerializeEventMetadataAsStableScalarValues() throws Exception {
        SampleDomainEvent event = new SampleDomainEvent("order-1");
        event.setAggregateVersion(new AggregateVersion(3));

        String json = objectMapper.writeValueAsString(event);

        assertThat(json)
                .contains("\"event-type\":\"SampleDomainEvent\"")
                .contains("\"event-id\":\"")
                .contains("\"entity-id-path\":\"String:order-1\"")
                .contains("\"aggregate-version\":3");
    }

    /**
     * 供传输、序列化或处理器分发验证的领域事件样本。
     */
    private static final class SampleDomainEvent extends DomainEvent<StringEntityId> {

        /**
         * 使用 <code>super()</code> 初始化 <code>SampleDomainEvent</code> 测试夹具。
         *
         * @param orderId 测试中要定位的订单标识
         */
        private SampleDomainEvent(String orderId) {
            super(orderId);
        }
    }
}
