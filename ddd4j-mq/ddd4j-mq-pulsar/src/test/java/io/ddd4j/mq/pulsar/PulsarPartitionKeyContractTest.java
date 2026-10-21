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
package io.ddd4j.mq.pulsar;

import io.ddd4j.mq.MQClient;
import io.ddd4j.mq.event.MQEvent;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Pulsar 五种分区键策略在三条发布线保持相同路由契约。
 */
class PulsarPartitionKeyContractTest {

    @Test
    void routesByConfiguredStrategy() {
        PulsarProperties properties = new PulsarProperties();
        PulsarMQClient client = new PulsarMQClient(properties);
        MQEvent event = new MQEvent();
        event.setTag("priority");
        event.setTenantId("tenant-a");

        properties.setPartitionKeyStrategy(MQClient.PartitionKeyStrategy.NONE);
        assertNull(client.partitionKey(event));
        properties.setPartitionKeyStrategy(MQClient.PartitionKeyStrategy.TAG);
        assertEquals("priority", client.partitionKey(event));
        properties.setPartitionKeyStrategy(MQClient.PartitionKeyStrategy.TENANT);
        assertEquals("tenant-a", client.partitionKey(event));
        properties.setPartitionKeyStrategy(MQClient.PartitionKeyStrategy.TAG_TENANT);
        assertEquals("priority|tenant-a", client.partitionKey(event));
        properties.setPartitionKeyStrategy(MQClient.PartitionKeyStrategy.CUSTOM);
        assertEquals("priority|tenant-a", client.partitionKey(event));
    }

    @Test
    void handlesMissingEventFields() {
        PulsarProperties properties = new PulsarProperties();
        PulsarMQClient client = new PulsarMQClient(properties);
        MQEvent event = new MQEvent();

        properties.setPartitionKeyStrategy(MQClient.PartitionKeyStrategy.TAG);
        assertNull(client.partitionKey(null));
        assertNull(client.partitionKey(event));
        properties.setPartitionKeyStrategy(MQClient.PartitionKeyStrategy.TENANT);
        assertNull(client.partitionKey(null));
        assertNull(client.partitionKey(event));
        properties.setPartitionKeyStrategy(MQClient.PartitionKeyStrategy.TAG_TENANT);
        assertNull(client.partitionKey(null));
        assertNull(client.partitionKey(event));
    }
}
