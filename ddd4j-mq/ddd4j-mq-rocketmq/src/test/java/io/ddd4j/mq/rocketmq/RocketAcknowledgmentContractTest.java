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
package io.ddd4j.mq.rocketmq;

import io.ddd4j.core.context.BaseContext;
import io.ddd4j.mq.MQProperties;
import io.ddd4j.mq.event.MQEvent;
import io.ddd4j.mq.event.MQEventSerialization;
import io.ddd4j.mq.listener.MQListener;
import org.apache.rocketmq.client.exception.MQClientException;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.common.message.Message;
import org.apache.rocketmq.common.message.MessageExt;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

/** \u901a\u8fc7 <code>message.setMsgId()</code>\u3001<code>acknowledgment.nack()</code>\u3001<code>acknowledgment.isAcknowledged()</code> \u9a8c\u8bc1 <code>RocketAcknowledgmentContract</code> \u7684\u8fd0\u884c\u884c\u4e3a\u3002 */
class RocketAcknowledgmentContractTest {

    /** \u6d4b\u8bd5\u7ed3\u675f\u540e\u6267\u884c <code>BaseContext.clear()</code> \u5b8c\u6210\u8d44\u6e90\u56de\u6536\u6216\u72b6\u6001\u6062\u590d\u3002 */
    @AfterEach
    void clearContext() {
        BaseContext.clear();
    }

    /** \u9a8c\u8bc1<code>acknowledgment.isAcknowledged()</code>\u4e3a\u771f\uff0c<code>acknowledgment.shouldReconsume()</code>\u4e3a\u771f\u3002 */
    @Test
    void shouldRequestBrokerReconsumeOnNack() {
        MessageExt message = new MessageExt();
        message.setMsgId("stable-id");
        RocketAcknowledgment acknowledgment = new RocketAcknowledgment(message);

        acknowledgment.nack(true);

        assertTrue(acknowledgment.isAcknowledged());
        assertTrue(acknowledgment.shouldReconsume());
    }

    /**
     * \u9a8c\u8bc1\u88ab\u6d4b\u8c03\u7528 \u629b\u51fa <code>IllegalStateException.class</code>\u3002
     * @throws Exception \u53d1\u8d77\u6d4b\u8bd5\u8bf7\u6c42\u65f6\u5931\u8d25
     */
    @Test
    void shouldPropagateBrokerSendFailureToPublisher() throws Exception {
        DefaultMQProducer producer = mock(DefaultMQProducer.class);
        doThrow(new MQClientException("broker unavailable", null)).when(producer).send(any(Message.class));
        RocketMQClient client = new RocketMQClient(producer);
        MQProperties properties = new MQProperties();
        properties.setEnabled(true);
        properties.setBroker("rocket");
        client.init(Collections.<MQListener>emptyList(), properties, new MQEventSerialization() {
            @Override
            @SuppressWarnings("unchecked")
            public <T> T serialize(Object event) {
                return (T) "{}";
            }

            @Override
            public <S, T> T deserialize(S value, Class<T> type) {
                return null;
            }
        }, null);
        MQEvent event = new MQEvent();
        event.setTopic("orders");

        assertThrows(IllegalStateException.class, event::publish);
    }
}
