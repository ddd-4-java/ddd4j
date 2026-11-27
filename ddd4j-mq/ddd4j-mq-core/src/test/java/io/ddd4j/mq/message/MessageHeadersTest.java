package io.ddd4j.mq.message;

import io.ddd4j.mq.delivery.MQDeliveryHeaders;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/** \u9a8c\u8bc1 <code>MessageHeaders</code> \u5728<code>messageIdHeader</code>\u7b49\u64cd\u4f5c\u4e2d\u7684\u884c\u4e3a\u5951\u7ea6\u3002 */
class MessageHeadersTest {

    /** \u9a8c\u8bc1<code>MessageHeaders.HEADER_MESSAGE_ID</code> \u7b49\u4e8e <code>MQDeliveryHeaders.MESSAGE_ID</code>\uff0c<code>MessageHeaders.HEADER_MESSAGE_ID</code> \u4e0d\u7b49\u4e8e <code>MessageHeaders.LEGACY_HEADER_MESSAGE_ID</code>\u3002 */
    @Test
    void messageIdHeader_shouldUseTheStableDeliveryContract() {
        assertEquals(MQDeliveryHeaders.MESSAGE_ID, MessageHeaders.HEADER_MESSAGE_ID);
        assertNotEquals(MessageHeaders.LEGACY_HEADER_MESSAGE_ID, MessageHeaders.HEADER_MESSAGE_ID);
    }
}
