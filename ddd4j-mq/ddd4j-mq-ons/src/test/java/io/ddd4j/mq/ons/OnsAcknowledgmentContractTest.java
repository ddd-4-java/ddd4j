package io.ddd4j.mq.ons;

import com.aliyun.openservices.ons.api.ConsumeContext;
import com.aliyun.openservices.ons.api.Message;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** \u901a\u8fc7 <code>mock()</code>\u3001<code>message.getMsgID()</code>\u3001<code>acknowledgment.nack()</code> \u9a8c\u8bc1 <code>OnsAcknowledgmentContract</code> \u7684\u8fd0\u884c\u884c\u4e3a\u3002 */
class OnsAcknowledgmentContractTest {

    /** \u9a8c\u8bc1<code>acknowledgment.action()</code> \u7b49\u4e8e <code>com.aliyun.openservices.ons.api.Action.ReconsumeLater</code>\uff0c<code>acknowledgment.isAcknowledged()</code>\u4e3a\u771f\u3002 */
    @Test
    void shouldMapNackToProtocolRetryAction() {
        Message message = mock(Message.class);
        when(message.getMsgID()).thenReturn("stable-id");
        OnsAcknowledgment acknowledgment = new OnsAcknowledgment(mock(ConsumeContext.class), message);

        acknowledgment.nack(true);

        assertEquals(com.aliyun.openservices.ons.api.Action.ReconsumeLater, acknowledgment.action());
        assertTrue(acknowledgment.isAcknowledged());
    }
}
