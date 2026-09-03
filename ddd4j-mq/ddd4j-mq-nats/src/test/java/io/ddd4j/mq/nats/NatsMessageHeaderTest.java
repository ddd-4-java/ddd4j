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
