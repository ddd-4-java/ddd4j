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

import java.io.*;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** \u901a\u8fc7 <code>value.asInt()</code>\u3001<code>roundTrip()</code>\u3001<code>value.asString()</code> \u9a8c\u8bc1 <code>EventIdentifierValueContract</code> \u7684\u8fd0\u884c\u884c\u4e3a\u3002 */
class EventIdentifierValueContractTest {

    /**
     * \u901a\u8fc7 <code>input.readObject()</code> \u53d6\u5f97\u5f53\u524d\u6d4b\u8bd5\u9700\u8981\u7684\u7ed3\u679c\u3002
     * @param value \u5f53\u524d\u7528\u4f8b\u4f20\u5165\u7684\u503c
     * @return \u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u7ed3\u679c\uff0c\u53d6\u81ea <code>input.readObject()</code>
     * @throws Exception \u51c6\u5907\u6216\u8bfb\u53d6\u5939\u5177\u6587\u4ef6\u65f6\u5931\u8d25
     */
    private static Object roundTrip(Serializable value) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            output.writeObject(value);
        }
        try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            return input.readObject();
        }
    }

    /**
     * \u9a8c\u8bc1<code>value.asInt()</code> \u7b49\u4e8e <code>7</code>\uff0c<code>value.toString()</code> \u7b49\u4e8e <code>"7"</code>\u3002
     * @throws Exception \u5f53\u524d\u7528\u4f8b\u51c6\u5907\u8d44\u6e90\u6216\u8c03\u7528\u6d4b\u8bd5\u5165\u53e3\u5931\u8d25
     */
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

    /**
     * \u9a8c\u8bc1<code>value.asString()</code> \u7b49\u4e8e <code>"OrderCreated"</code>\uff0c<code>value.toString()</code> \u7b49\u4e8e <code>"OrderCreated"</code>\u3002
     * @throws Exception \u5f53\u524d\u7528\u4f8b\u51c6\u5907\u8d44\u6e90\u6216\u8c03\u7528\u6d4b\u8bd5\u5165\u53e3\u5931\u8d25
     */
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

    /**
     * \u9a8c\u8bc1<code>value.asString()</code> \u7b49\u4e8e <code>"Order"</code>\uff0c<code>value.toString()</code> \u7b49\u4e8e <code>"Order"</code>\u3002
     * @throws Exception \u5f53\u524d\u7528\u4f8b\u51c6\u5907\u8d44\u6e90\u6216\u8c03\u7528\u6d4b\u8bd5\u5165\u53e3\u5931\u8d25
     */
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

    /**
     * \u9a8c\u8bc1<code>value.asUuid()</code> \u7b49\u4e8e <code>uuid</code>\uff0c<code>value.asString()</code> \u7b49\u4e8e <code>uuid.toString()</code>\u3002
     * @throws Exception \u5f53\u524d\u7528\u4f8b\u51c6\u5907\u8d44\u6e90\u6216\u8c03\u7528\u6d4b\u8bd5\u5165\u53e3\u5931\u8d25
     */
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
}
