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

    /**
     * 验证<code>processor.getListeners().size()</code> 等于 <code>1</code>，<code>processor.getListeners().get(0).getTopic()</code> 等于 <code>"orders"</code>，<code>processor.getListeners().size()</code> 等于 <code>2</code>。
     */
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

    /**
     * 提供 <code>onPaid()</code> 等供当前测试使用的独立操作。
     */
    static final class OrderListener {

        /**
         * 作为空实现的测试替身，调用时保持当前夹具状态。
         *
         * @param event 交给被测入口处理的测试领域事件
         */
        @MQEventListener(topic = "orders", tags = "paid")
        public void onPaid(OrderPaidEvent event) {
        }
    }

    /**
     * 表示测试订单已支付的领域事件。
     */
    static final class OrderPaidEvent extends MQEvent {
    }
}
