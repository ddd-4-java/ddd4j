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
package io.ddd4j.mq.nats;

import io.ddd4j.mq.event.MQEvent;
import io.ddd4j.mq.message.MessageHeaders;
import io.nats.client.impl.Headers;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NatsMessageHeaderTest {

    @Test
    void shouldWriteStableHeaderAndPreferItWhenReading() {
        MQEvent event = new MQEvent();
        event.setMsgId("stable-id");
        Headers headers = NatsMQClient.messageHeaders(event);
        headers.put(MessageHeaders.LEGACY_HEADER_MESSAGE_ID, "legacy-id");

        assertEquals("stable-id", headers.getFirst(MessageHeaders.HEADER_MESSAGE_ID));
        assertEquals("stable-id", NatsMQClient.messageId(headers));

        Headers legacyHeaders = new Headers();
        legacyHeaders.put(MessageHeaders.LEGACY_HEADER_MESSAGE_ID, "legacy-id");
        assertEquals("legacy-id", NatsMQClient.messageId(legacyHeaders));
    }
}
