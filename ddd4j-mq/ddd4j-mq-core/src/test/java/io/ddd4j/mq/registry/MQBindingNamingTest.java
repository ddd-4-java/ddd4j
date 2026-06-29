package io.ddd4j.mq.registry;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * {@link MQBindingNaming} 绑定命名单测。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
class MQBindingNamingTest {

    @Test
    void orderPaidNotifyExample() {
        assertEquals("orderPaidNotify", MQBindingNaming.bindingName("order.paid", "notify"));
    }

    @Test
    void hyphenAndUnderscoreTopic() {
        assertEquals("orderPaidNotify", MQBindingNaming.bindingName("order-paid", "notify"));
        assertEquals("orderPaidNotify", MQBindingNaming.bindingName("order_paid", "notify"));
    }

    @Test
    void wildcardTagUsesTopicOnly() {
        assertEquals("orderPaid", MQBindingNaming.bindingName("order.paid", "*"));
    }

    @Test
    void compositeTagTakesFirstSegment() {
        assertEquals("orderPaidNotify", MQBindingNaming.bindingName("order.paid", "notify || billing"));
    }

    @Test
    void inboundBindingName() {
        assertEquals("orderPaidNotify-in-0", MQBindingNaming.inboundBindingName("orderPaidNotify"));
    }

    @Test
    void outboundBindingName() {
        assertEquals("orderPaidNotify-out-0", MQBindingNaming.outboundBindingName("orderPaidNotify"));
    }

    @Test
    void emptyTopicFallbackDefault() {
        assertEquals("default", MQBindingNaming.bindingName("", ""));
    }

    @Test
    void brokerTypeFromConfig() {
        assertEquals(MQBrokerType.RABBIT, MQBrokerType.fromConfig("rabbit"));
        assertEquals(MQBrokerType.MQTT, MQBrokerType.fromConfig("mqtt"));
        assertEquals(MQBrokerType.MQTT_MICA, MQBrokerType.fromConfig("mqtt-mica"));
        assertEquals(MQBrokerType.REDIS_STREAM, MQBrokerType.fromConfig("redis-stream"));
        assertEquals(MQBrokerType.NONE, MQBrokerType.fromConfig("unknown"));
    }
}
