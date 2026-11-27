package io.ddd4j.mq.activemq;

import io.ddd4j.mq.message.MessageHeaders;
import jakarta.jms.Message;
import jakarta.jms.Session;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** ActiveMQ \u56e0\u679c\u5934\u3001\u6d88\u606f\u8bfb\u53d6\u4e0e\u8fde\u63a5\u751f\u547d\u5468\u671f\u4e09\u7ebf\u5951\u7ea6\u3002 */
class ActiveMQAdapterContractTest {

    /**
     * 验证<code>ActiveMQClient.messageId(message)</code> 等于 <code>"stable-id"</code>，<code>acknowledgment.isAcknowledged()</code>为真。
     *
     * @throws Exception 完成 <code>should Prefer Stable Message Id And Requeue On Nack</code> 操作失败
     */
    @Test
    void shouldPreferStableMessageIdAndRequeueOnNack() throws Exception {
        Message message = mock(Message.class);
        Session session = mock(Session.class);
        when(message.getStringProperty(MessageHeaders.HEADER_MESSAGE_ID)).thenReturn("stable-id");
        when(message.getStringProperty(MessageHeaders.LEGACY_HEADER_MESSAGE_ID)).thenReturn("legacy-id");

        assertEquals("stable-id", ActiveMQClient.messageId(message));

        ActiveMQAcknowledgment acknowledgment = new ActiveMQAcknowledgment(session, message, 7L, "stable-id", null);
        acknowledgment.nack(true);

        verify(session).recover();
        assertTrue(acknowledgment.isAcknowledged());
    }
}
