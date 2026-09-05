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

import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DomainEventJsonTest {

    // Jackson 3 内建 JavaTimeModule（自动注册），无需手动 registerModule
    private final ObjectMapper objectMapper = new ObjectMapper();

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

    private static final class SampleDomainEvent extends DomainEvent<StringEntityId> {

        private SampleDomainEvent(String orderId) {
            super(orderId);
        }
    }
}
