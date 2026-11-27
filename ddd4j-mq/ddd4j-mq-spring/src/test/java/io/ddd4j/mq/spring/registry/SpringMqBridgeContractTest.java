/*
 * Copyright (c) 2024-2026 ddd4j project. All rights reserved.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.ddd4j.mq.spring.registry;

import io.ddd4j.mq.MQProperties;
import io.ddd4j.mq.annotation.MQEventListener;
import io.ddd4j.mq.event.MQEvent;
import io.ddd4j.mq.listener.MQListener;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

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
        assertTrue(processor.getListeners().get(0).isRequired());
        assertEquals(Collections.singletonList("*"), processor.getListeners().get(0).getSupports());

        List<MQListener> snapshot = processor.getListeners();
        assertThrows(UnsupportedOperationException.class, () -> snapshot.clear());
        processor.postProcessAfterInitialization(new OrderListener(), "secondOrderListener");
        assertEquals(1, snapshot.size());
        assertEquals(2, processor.getListeners().size());
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
