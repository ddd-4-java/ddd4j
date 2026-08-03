package io.ddd4j.mq.spring.registry;

import io.ddd4j.mq.MQProperties;
import io.ddd4j.mq.annotation.MQEventListener;
import io.ddd4j.mq.event.MQEvent;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Spring 模块只发现并装配监听器，不承载 broker 报文；ddd4j-message-id 由实际 adapter 负责。
 */
class SpringMqBridgeContractTest {

    @Test
    void shouldDiscoverListenerWithoutDefiningBrokerMessageSemantics() {
        MQProperties properties = new MQProperties();
        properties.setEnabled(true);
        properties.setNamespace("sales");
        MQListenerBeanPostProcessor processor = new MQListenerBeanPostProcessor(properties);

        processor.postProcessAfterInitialization(new OrderListener(), "orderListener");

        assertEquals(1, processor.getListeners().size());
        assertEquals("orders", processor.getListeners().get(0).getTopic());
    }

    static final class OrderListener {

        @MQEventListener(topic = "orders", tags = "paid")
        public void onPaid(OrderPaidEvent event) {
        }
    }

    static final class OrderPaidEvent extends MQEvent {
    }
}
