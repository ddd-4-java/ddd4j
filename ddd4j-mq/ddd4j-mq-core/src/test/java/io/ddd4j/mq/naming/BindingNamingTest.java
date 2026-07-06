package io.ddd4j.mq.listener;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * {@link BindingNaming} 绑定命名单测。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
class BindingNamingTest {

    @Test
    void orderPaidNotifyExample() {
        assertEquals("orderPaidNotify", BindingNaming.bindingName("order.paid", "notify"));
    }

    @Test
    void hyphenAndUnderscoreTopic() {
        assertEquals("orderPaidNotify", BindingNaming.bindingName("order-paid", "notify"));
        assertEquals("orderPaidNotify", BindingNaming.bindingName("order_paid", "notify"));
    }

    @Test
    void wildcardTagUsesTopicOnly() {
        assertEquals("orderPaid", BindingNaming.bindingName("order.paid", "*"));
    }

    @Test
    void compositeTagTakesFirstSegment() {
        assertEquals("orderPaidNotify", BindingNaming.bindingName("order.paid", "notify || billing"));
    }

    @Test
    void inboundBindingName() {
        assertEquals("orderPaidNotify-in-0", BindingNaming.inboundBindingName("orderPaidNotify"));
    }

    @Test
    void outboundBindingName() {
        assertEquals("orderPaidNotify-out-0", BindingNaming.outboundBindingName("orderPaidNotify"));
    }

    @Test
    void emptyTopicFallbackDefault() {
        assertEquals("default", BindingNaming.bindingName("", ""));
    }

    @Test
    void brokerTypeFromConfig() {
        assertEquals(BrokerType.RABBIT, BrokerType.from("rabbit"));
        assertEquals(BrokerType.MQTT, BrokerType.from("mqtt"));
        assertEquals(BrokerType.MQTT_MICA, BrokerType.from("mqtt-mica"));
        assertEquals(BrokerType.REDIS_STREAM, BrokerType.from("redis-stream"));
        assertEquals(BrokerType.NONE, BrokerType.from("unknown"));
    }
}
