package io.ddd4j.mq.nats;

import io.ddd4j.mq.event.MQEvent;
import io.ddd4j.mq.message.MessageHeaders;
import io.nats.client.impl.Headers;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** \u9a8c\u8bc1 <code>NatsMessageHeader</code> \u5728 <code>shouldWriteStableHeaderAndPreferIt</code> \u7b49\u573a\u666f\u4e2d\u7684\u884c\u4e3a\u3002 */
class NatsMessageHeaderTest {

    /** \u9a8c\u8bc1<code>headers.getFirst(MessageHeaders.HEADER_MESSAGE_ID)</code> \u7b49\u4e8e <code>"stable-id"</code>\uff0c<code>headers.getFirst(MQDeliveryHeaders.CORRELATION_ID)</code> \u7b49\u4e8e <code>"correlation-1"</code>\uff0c<code>NatsMQClient.messageId(legacyHeaders)</code> \u7b49\u4e8e <code>"legacy-id"</code>\u3002 */
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
