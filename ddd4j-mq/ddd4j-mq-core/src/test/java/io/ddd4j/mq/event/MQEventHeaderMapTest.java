package io.ddd4j.mq.event;

import com.fasterxml.jackson.databind.json.JsonMapper;
import io.ddd4j.mq.delivery.MQDeliveryHeaders;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MQEventHeaderMapTest {

    @Test
    void exposesOnlyPresentCausalityAsImmutableBrokerHeaders() {
        MQEvent event = new MQEvent();
        event.setMsgId("event-1");
        event.setCorrelationId("correlation-1");
        event.setCausationId("cause-1");

        Map<String, String> headers = event.headerMap();
        assertEquals("event-1", headers.get(MQDeliveryHeaders.MESSAGE_ID));
        assertEquals("correlation-1", headers.get(MQDeliveryHeaders.CORRELATION_ID));
        assertEquals("cause-1", headers.get(MQDeliveryHeaders.CAUSATION_ID));
        assertThrows(UnsupportedOperationException.class,
                () -> headers.put(MQDeliveryHeaders.CORRELATION_ID, "changed"));
    }

    @Test
    void omitsAbsentCausalityAndAvoidsDuplicatePayloadFields() throws Exception {
        MQEvent event = new MQEvent();
        event.setMsgId("event-2");

        assertFalse(event.headerMap().containsKey(MQDeliveryHeaders.CORRELATION_ID));
        assertFalse(event.headerMap().containsKey(MQDeliveryHeaders.CAUSATION_ID));

        event.setCorrelationId("correlation-2");
        String payload = JsonMapper.builder().build().writeValueAsString(event);
        assertFalse(payload.contains("correlationId"));
        assertFalse(payload.contains("causationId"));
    }
}
