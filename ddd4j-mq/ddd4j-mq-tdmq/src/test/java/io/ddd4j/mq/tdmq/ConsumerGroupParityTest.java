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
package io.ddd4j.mq.tdmq;

import io.ddd4j.mq.MQProperties;
import io.ddd4j.mq.listener.MQListener;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 通过真实订阅入口观察 group，不访问外部 Broker。
 */
class ConsumerGroupParityTest {
    /**
     * \u5728\u6d4b\u8bd5\u6846\u67b6\u4e2d\u6267\u884c <code>properties.setDefaultGroup()</code>\u3001<code>client.setBrokerSubscriber()</code>\u3001<code>groups.add()</code>\uff0c\u9a8c\u8bc1\u8be5\u573a\u666f\u3002
     * @throws Exception \u5f53\u524d\u7528\u4f8b\u51c6\u5907\u8d44\u6e90\u6216\u8c03\u7528\u6d4b\u8bd5\u5165\u53e3\u5931\u8d25
     */
    @Test
    void whitespaceGroupFallsBackAndExplicitGroupIsPreserved() throws Exception {
        TdmqProperties properties = new TdmqProperties();
        properties.setDefaultGroup("configured-group");
        List<String> groups = new ArrayList<>();
        TdmqMQClient client = new TdmqMQClient(properties);
        client.setBrokerSubscriber((topic, tags, group, handler) -> {
            groups.add(group);
            return () -> {
            };
        });
        try {
            for (String group : new String[]{" \t", "", "explicit-group"}) {
                client.initConsumer(MQListener.builder().group(group).topic("topic").tags("*").build(), new MQProperties());
            }
            assertEquals(Arrays.asList("configured-group", "configured-group", "explicit-group"), groups);
        } finally {
            client.close();
        }
    }


    /** \u9a8c\u8bc1<code>closeCount.get()</code> \u7b49\u4e8e <code>1</code>\u3002 */
    @Test
    void closesEverySubscriptionOnlyOnce() {
        TdmqProperties properties = new TdmqProperties();
        AtomicInteger closeCount = new AtomicInteger();
        TdmqMQClient client = new TdmqMQClient(properties);
        client.setBrokerSubscriber((topic, tags, group, handler) -> closeCount::incrementAndGet);
        client.initConsumer(MQListener.builder().group("group").topic("topic").tags("*").build(),
                new MQProperties());

        client.close();
        client.close();

        assertEquals(1, closeCount.get());
    }
}
