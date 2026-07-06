package io.ddd4j.mq.message;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * {@link Message#nativeMessage(Class)} 单元测试。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
class MessageNativeMessageTest {

    @Test
    void nativeMessageShouldReturnMatchingInstance() {
        Object nativeHandle = "native-payload";
        Message<String> message = Message.of("payload", Map.of(), "id-1", "corr-1", nativeHandle);

        assertEquals(nativeHandle, message.nativeMessage(String.class));
        assertNull(message.nativeMessage(Integer.class));
        assertNull(Message.of("x", "id").nativeMessage(String.class));
    }
}
