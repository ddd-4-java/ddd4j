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

class RocketAcknowledgmentContractTest {

    @AfterEach
    void clearContext() {
        BaseContext.clear();
    }

    @Test
    void shouldRequestBrokerReconsumeOnNack() {
        MessageExt message = new MessageExt();
        message.setMsgId("stable-id");
        RocketAcknowledgment acknowledgment = new RocketAcknowledgment(message);

        acknowledgment.nack(true);

        assertTrue(acknowledgment.isAcknowledged());
        assertTrue(acknowledgment.shouldReconsume());
    }

    @Test
    void shouldPropagateBrokerSendFailureToPublisher() throws Exception {
        DefaultMQProducer producer = mock(DefaultMQProducer.class);
        doThrow(new MQClientException("broker unavailable", null)).when(producer).send(any(Message.class));
        RocketMQClient client = new RocketMQClient(producer);
        MQProperties properties = new MQProperties();
        properties.setEnabled(true);
        properties.setBroker("rocket");
        client.init(Collections.<MQListener>emptyList(), properties, new MQEventSerialization() {
            @Override @SuppressWarnings("unchecked") public <T> T serialize(Object event) { return (T) "{}"; }
            @Override public <S, T> T deserialize(S value, Class<T> type) { return null; }
        }, null);
        MQEvent event = new MQEvent();
        event.setTopic("orders");

        assertThrows(IllegalStateException.class, event::publish);
    }
}
