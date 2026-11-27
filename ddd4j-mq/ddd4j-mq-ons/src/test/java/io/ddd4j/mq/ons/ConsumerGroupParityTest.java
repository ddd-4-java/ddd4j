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
package io.ddd4j.mq.ons;

import io.ddd4j.mq.MQProperties;
import io.ddd4j.mq.listener.MQListener;
import org.junit.jupiter.api.Test;

import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 参数必须在任何 ONS 客户端创建之前完成校验。
 */
class ConsumerGroupParityTest {
    /**
     * \u6784\u9020 <code>OnsProperties</code> \u4f5c\u4e3a\u5f53\u524d\u6d4b\u8bd5\u7684\u8f93\u5165\u6216\u7ed3\u679c\u3002
     * @return \u63d0\u4f9b\u7ed9\u540e\u7eed\u5951\u7ea6\u65ad\u8a00\u7684 <code>OnsProperties</code> \u7ed3\u679c
     */
    private static OnsProperties guardedProperties() {
        return new OnsProperties() {
            /**
             * sessionProperties() 方法。
             * @param groupName 字符串参数。
             * @return 方法执行结果。
             */
            @Override
            public Properties sessionProperties(String groupName) {
                throw new AssertionError("Invalid input reached broker creation");
            }
        };
    }

    /** \u9a8c\u8bc1\u88ab\u6d4b\u8c03\u7528 \u629b\u51fa <code>IllegalStateException.class</code>\u3002 */
    @Test
    void whitespaceGroupIsRejectedBeforeCreatingBroker() {
        OnsProperties properties = guardedProperties();
        properties.setConsumerId(" \t");
        properties.setTopic("topic");
        MQListener listener = MQListener.builder().group(" \t").topic("topic").tags("*").build();
        assertThrows(IllegalStateException.class, () -> new OnsMQClient(properties).initConsumer(listener, new MQProperties()));
    }

    /** \u9a8c\u8bc1\u88ab\u6d4b\u8c03\u7528 \u629b\u51fa <code>IllegalStateException.class</code>\u3002 */
    @Test
    void whitespaceTopicIsRejectedBeforeCreatingBroker() {
        OnsProperties properties = guardedProperties();
        properties.setConsumerId("group");
        properties.setTopic(" \t");
        MQListener listener = MQListener.builder().group("group").topic(" \t").tags("*").build();
        assertThrows(IllegalStateException.class, () -> new OnsMQClient(properties).initConsumer(listener, new MQProperties()));
    }
}
