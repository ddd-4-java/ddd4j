package io.ddd4j.mq.ons;

import com.aliyun.openservices.ons.api.Message;
import io.ddd4j.mq.message.MessageHeaders;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** \u9a8c\u8bc1 <code>OnsMessageHeader</code> \u5728 <code>acknowledgment</code>\u3001<code>nativeProperties</code> \u7b49\u573a\u666f\u4e2d\u7684\u884c\u4e3a\u3002 */
class OnsMessageHeaderTest {

    /** \u9a8c\u8bc1<code>OnsMQClient.messageId(stableMessage)</code> \u7b49\u4e8e <code>"stable-id"</code>\uff0c<code>OnsMQClient.messageId(legacyMessage)</code> \u7b49\u4e8e <code>"legacy-id"</code>\u3002 */
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
