package io.ddd4j.mq.event;

import com.fasterxml.jackson.databind.json.JsonMapper;
import io.ddd4j.mq.delivery.MQDeliveryHeaders;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 通过 <code>event.setMsgId()</code>、<code>event.setCorrelationId()</code>、<code>event.setCausationId()</code> 验证 <code>MQEventHeaderMap</code> 的运行行为。
 */
class MQEventHeaderMapTest {

    /**
     * 验证<code>headers.get(MQDeliveryHeaders.MESSAGE_ID)</code> 等于 <code>"event-1"</code>，<code>headers.get(MQDeliveryHeaders.CORRELATION_ID)</code> 等于 <code>"correlation-1"</code>，<code>headers.put(MQDeliveryHeaders.CORRELATION_ID, "changed")</code> 抛出 <code>UnsupportedOperationException.class</code>。
     */
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
     * 验证<code>event.headerMap().containsKey(MQDeliveryHeaders.CORRELATION_ID)</code>为假，<code>event.headerMap().containsKey(MQDeliveryHeaders.CAUSATION_ID)</code>为假，<code>payload.contains("causationId")</code>为假。
     *
     * @throws Exception 处理测试数据的序列化时失败
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
