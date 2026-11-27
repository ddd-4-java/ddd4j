package io.ddd4j.mq.rocketmq;

import io.ddd4j.mq.message.MessageHeaders;
import org.apache.rocketmq.common.message.MessageExt;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * \u7c7b\u578b\u8bf4\u660e\u3002
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 2.0.x
 */
class RocketMessageHeaderTest {

    /** \u9a8c\u8bc1<code>RocketMQClient.messageId(stableMessage)</code> \u7b49\u4e8e <code>"stable-id"</code>\uff0c<code>RocketMQClient.messageId(legacyMessage)</code> \u7b49\u4e8e <code>"legacy-id"</code>\u3002 */
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
