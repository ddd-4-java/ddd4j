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
