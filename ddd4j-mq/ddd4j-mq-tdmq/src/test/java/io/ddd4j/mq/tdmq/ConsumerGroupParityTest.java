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
import static org.junit.jupiter.api.Assertions.assertEquals;

/** 通过真实订阅入口观察 group，不访问外部 Broker。 */
class ConsumerGroupParityTest {
    @Test
    void whitespaceGroupFallsBackAndExplicitGroupIsPreserved() throws Exception {
        TdmqProperties properties = new TdmqProperties();
        properties.setDefaultGroup("configured-group");
        List<String> groups = new ArrayList<>();
        TdmqMQClient client = new TdmqMQClient(properties);
        client.setBrokerSubscriber((topic, tags, group, handler) -> {
            groups.add(group);
            return () -> { };
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
}
