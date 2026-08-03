package io.ddd4j.mq.rocketmq;

import io.ddd4j.mq.message.MessageHeaders;
import org.apache.rocketmq.common.message.MessageExt;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RocketMessageHeaderTest {

    @Test
    void shouldPreferStableHeaderAndReadLegacyHeader() {
        MessageExt stableMessage = new MessageExt();
        stableMessage.putUserProperty(MessageHeaders.HEADER_MESSAGE_ID, "stable-id");
        stableMessage.putUserProperty(MessageHeaders.LEGACY_HEADER_MESSAGE_ID, "legacy-id");
        assertEquals("stable-id", RocketMQClient.messageId(stableMessage));

        MessageExt legacyMessage = new MessageExt();
        legacyMessage.putUserProperty(MessageHeaders.LEGACY_HEADER_MESSAGE_ID, "legacy-id");
        assertEquals("legacy-id", RocketMQClient.messageId(legacyMessage));
    }
}
