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

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EventIdentifierValueContractTest {

    @Test
    void aggregateVersionHasStableValueSemantics() throws Exception {
        AggregateVersion value = new AggregateVersion(7);
        assertThat(value.asInt()).isEqualTo(7);
        assertThat(value.toString()).isEqualTo("7");
        assertThat(value).isEqualTo(new AggregateVersion(7)).hasSameHashCodeAs(new AggregateVersion(7));
        assertThat(value).isNotEqualTo(new AggregateVersion(8)).isNotEqualTo("7");
        assertThat(roundTrip(value)).isEqualTo(value);
        assertThatThrownBy(() -> new AggregateVersion(-1)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void eventTypeHasStableValueSemantics() throws Exception {
        EventType value = new EventType("OrderCreated");
        assertThat(value.asString()).isEqualTo("OrderCreated");
        assertThat(value.toString()).isEqualTo("OrderCreated");
        assertThat(value).isEqualTo(new EventType("OrderCreated")).hasSameHashCodeAs(new EventType("OrderCreated"));
        assertThat(value).isNotEqualTo(new EventType("OrderUpdated")).isNotEqualTo("OrderCreated");
        assertThat(roundTrip(value)).isEqualTo(value);
        assertThatThrownBy(() -> new EventType(" ")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new EventType(new String(new char[256]).replace('\0', 'a')))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void stringEntityTypeHasStableValueSemantics() throws Exception {
        StringEntityType value = new StringEntityType("Order");
        assertThat(value.asString()).isEqualTo("Order");
        assertThat(value.toString()).isEqualTo("Order");
        assertThat(value).isEqualTo(new StringEntityType("Order")).hasSameHashCodeAs(new StringEntityType("Order"));
        assertThat(value).isNotEqualTo(new StringEntityType("Customer")).isNotEqualTo("Order");
        assertThat(roundTrip(value)).isEqualTo(value);
        assertThatThrownBy(() -> new StringEntityType(" ")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void eventIdHasStableValueSemantics() throws Exception {
        UUID uuid = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");
        EventId value = new EventId(uuid);
        assertThat(value.asUuid()).isEqualTo(uuid);
        assertThat(value.asString()).isEqualTo(uuid.toString());
        assertThat(value.toString()).isEqualTo(uuid.toString());
        assertThat(value).isEqualTo(EventId.valueOf(uuid.toString())).hasSameHashCodeAs(new EventId(uuid));
        assertThat(value).isNotEqualTo(new EventId(UUID.fromString("123e4567-e89b-12d3-a456-426614174001")))
                .isNotEqualTo(uuid.toString());
        assertThat(roundTrip(value)).isEqualTo(value);
        assertThat(EventId.valueOf(" ")).isNull();
        assertThatThrownBy(() -> new EventId(null)).isInstanceOf(NullPointerException.class);
    }

    private static Object roundTrip(Serializable value) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            output.writeObject(value);
        }
        try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            return input.readObject();
        }
    }
}
