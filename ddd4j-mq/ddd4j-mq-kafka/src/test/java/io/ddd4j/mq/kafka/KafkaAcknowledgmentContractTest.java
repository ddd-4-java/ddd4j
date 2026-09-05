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
package io.ddd4j.mq.kafka;

import io.ddd4j.mq.message.MessageHeaders;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class KafkaAcknowledgmentContractTest {

    @Test
    void shouldReadStableMessageIdAndCommitOffsetOnAck() {
        ConsumerRecord<String, String> record = new ConsumerRecord<>("orders", 1, 8L, "key", "body");
        record.headers().add(MessageHeaders.HEADER_MESSAGE_ID, "stable-id".getBytes(StandardCharsets.UTF_8));
        Consumer<?, ?> consumer = mock(Consumer.class);
        KafkaMessageAcknowledgment acknowledgment = new KafkaMessageAcknowledgment(consumer, record);

        assertEquals("stable-id", acknowledgment.messageId());
        acknowledgment.ack();

        verify(consumer).commitSync(anyMap());
        assertTrue(acknowledgment.isAcknowledged());
    }
}
