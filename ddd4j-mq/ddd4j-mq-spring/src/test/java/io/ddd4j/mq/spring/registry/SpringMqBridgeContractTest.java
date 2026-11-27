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

    /** \u9a8c\u8bc1<code>processor.getListeners().size()</code> \u7b49\u4e8e <code>1</code>\uff0c<code>processor.getListeners().get(0).getTopic()</code> \u7b49\u4e8e <code>"orders"</code>\uff0c<code>processor.getListeners().size()</code> \u7b49\u4e8e <code>2</code>\u3002 */
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

    /** \u63d0\u4f9b <code>onPaid()</code> \u7b49\u4f9b\u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u72ec\u7acb\u64cd\u4f5c\u3002 */
    static final class OrderListener {

        /**
         * \u4f5c\u4e3a\u7a7a\u5b9e\u73b0\u7684\u6d4b\u8bd5\u66ff\u8eab\uff0c\u8c03\u7528\u65f6\u4fdd\u6301\u5f53\u524d\u5939\u5177\u72b6\u6001\u3002
         * @param event \u4ea4\u7ed9\u88ab\u6d4b\u5165\u53e3\u5904\u7406\u7684\u6d4b\u8bd5\u9886\u57df\u4e8b\u4ef6
         */
        @MQEventListener(topic = "orders", tags = "paid")
        public void onPaid(OrderPaidEvent event) {
        }
    }

    /** \u8868\u793a\u6d4b\u8bd5\u8ba2\u5355\u5df2\u652f\u4ed8\u7684\u9886\u57df\u4e8b\u4ef6\u3002 */
    static final class OrderPaidEvent extends MQEvent {
    }
}
