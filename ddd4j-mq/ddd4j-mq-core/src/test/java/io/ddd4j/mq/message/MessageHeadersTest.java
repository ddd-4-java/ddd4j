package io.ddd4j.mq.message;

import io.ddd4j.mq.delivery.MQDeliveryHeaders;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class MessageHeadersTest {

    @Test
    void messageIdHeader_shouldUseTheStableDeliveryContract() {
        assertEquals(MQDeliveryHeaders.MESSAGE_ID, MessageHeaders.HEADER_MESSAGE_ID);
        assertNotEquals(MessageHeaders.LEGACY_HEADER_MESSAGE_ID, MessageHeaders.HEADER_MESSAGE_ID);
    }
}
