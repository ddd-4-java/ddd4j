package io.ddd4j.mq.ons;

import com.aliyun.openservices.ons.api.ConsumeContext;
import com.aliyun.openservices.ons.api.Message;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OnsAcknowledgmentContractTest {

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
