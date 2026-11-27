package io.ddd4j.mq.event;

import com.fasterxml.jackson.databind.json.JsonMapper;
import io.ddd4j.mq.delivery.MQDeliveryHeaders;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** \u901a\u8fc7 <code>event.setMsgId()</code>\u3001<code>event.setCorrelationId()</code>\u3001<code>event.setCausationId()</code> \u9a8c\u8bc1 <code>MQEventHeaderMap</code> \u7684\u8fd0\u884c\u884c\u4e3a\u3002 */
class MQEventHeaderMapTest {

    /** \u9a8c\u8bc1<code>headers.get(MQDeliveryHeaders.MESSAGE_ID)</code> \u7b49\u4e8e <code>"event-1"</code>\uff0c<code>headers.get(MQDeliveryHeaders.CORRELATION_ID)</code> \u7b49\u4e8e <code>"correlation-1"</code>\uff0c<code>headers.put(MQDeliveryHeaders.CORRELATION_ID, "changed")</code> \u629b\u51fa <code>UnsupportedOperationException.class</code>\u3002 */
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

    /**
     * \u9a8c\u8bc1<code>event.headerMap().containsKey(MQDeliveryHeaders.CORRELATION_ID)</code>\u4e3a\u5047\uff0c<code>event.headerMap().containsKey(MQDeliveryHeaders.CAUSATION_ID)</code>\u4e3a\u5047\uff0c<code>payload.contains("causationId")</code>\u4e3a\u5047\u3002
     * @throws Exception \u5904\u7406\u6d4b\u8bd5\u6570\u636e\u7684\u5e8f\u5217\u5316\u65f6\u5931\u8d25
     */
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
