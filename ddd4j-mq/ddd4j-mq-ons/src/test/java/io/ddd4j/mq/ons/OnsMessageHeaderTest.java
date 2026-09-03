package io.ddd4j.mq.ons;

import com.aliyun.openservices.ons.api.Message;
import io.ddd4j.mq.message.MessageHeaders;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OnsMessageHeaderTest {

    @Test
    void shouldPreferStableHeaderAndReadLegacyHeader() {
        Message stableMessage = new Message();
        stableMessage.putUserProperties(MessageHeaders.HEADER_MESSAGE_ID, "stable-id");
        stableMessage.putUserProperties(MessageHeaders.LEGACY_HEADER_MESSAGE_ID, "legacy-id");
        assertEquals("stable-id", OnsMQClient.messageId(stableMessage));

        Message legacyMessage = new Message();
        legacyMessage.putUserProperties(MessageHeaders.LEGACY_HEADER_MESSAGE_ID, "legacy-id");
        assertEquals("legacy-id", OnsMQClient.messageId(legacyMessage));
    }
}
