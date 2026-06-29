package io.ddd4j.mq.contract;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * {@link MQMessage#nativeMessage(Class)} 单元测试。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
class MQMessageNativeMessageTest {

    @Test
    void nativeMessageShouldReturnMatchingInstance() {
        Object nativeHandle = "native-payload";
        MQMessage<String> message = MQMessage.of("payload", Map.of(), "id-1", "corr-1", nativeHandle);

        assertEquals(nativeHandle, message.nativeMessage(String.class));
        assertNull(message.nativeMessage(Integer.class));
        assertNull(MQMessage.of("x", "id").nativeMessage(String.class));
    }
}
