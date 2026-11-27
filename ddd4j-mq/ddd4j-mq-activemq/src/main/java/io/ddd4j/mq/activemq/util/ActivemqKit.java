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
package io.ddd4j.mq.activemq.util;
import jakarta.jms.*;

import io.ddd4j.kit.lang.StrKit;
import jakarta.jms.BytesMessage;
import jakarta.jms.Destination;
import jakarta.jms.JMSException;
import jakarta.jms.Message;
import jakarta.jms.Session;
import jakarta.jms.TextMessage;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * ActiveMQ (JMS) 工具类：抽离 ActiveMQClient 内的纯 JMS 字符串解析与提取逻辑，
 * 与 core 解耦可单测。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 2.0.x
 */
public final class ActivemqKit {

    /**
     * \u9650\u5236\u8c03\u7528\u65b9\u76f4\u63a5\u521b\u5efa <code>ActivemqKit</code> \u5b9e\u4f8b\u3002
     */
    private ActivemqKit() {
    }

    /**
     * \u62fc\u63a5\u7269\u7406\u5730\u5740\uff1a{@code namespace.topic[.tag]}\uff0c
     *  \u652f\u6301 {@code queue:} / {@code topic:} \u524d\u7f00\u5f3a\u5236\u7c7b\u578b\u3002
     *
     * @param namespace \u6d88\u606f\u547d\u540d\u7a7a\u95f4
     * @param topic \u4e3b\u9898\u540d\u79f0
     * @param tag \u6d88\u606f\u6807\u7b7e
     * @return \u7269\u7406\u76ee\u7684\u5730\u540d\u79f0
     */
    public static String resolvePhysical(String namespace, String topic, String tag) {
        String base = StrKit.hasText(topic) ? topic : "ddd4j.default.topic";
        if (StrKit.hasText(namespace)) {
            base = namespace + "." + base;
        }
        return StrKit.hasText(tag) ? base + "." + tag : base;
    }

    /**
     * \u6839\u636e\u7269\u7406\u5730\u5740\u524d\u7f00\u521b\u5efa JMS Destination\u3002
     *
     * @param session JMS \u4f1a\u8bdd
     * @param physical \u7269\u7406\u76ee\u7684\u5730\u540d\u79f0
     * @return JMS \u76ee\u7684\u5730
     * @throws JMSException \u521b\u5efa\u76ee\u7684\u5730\u5931\u8d25\u65f6\u629b\u51fa
     */
    public static Destination createDestination(Session session, String physical) throws JMSException {
        if (physical.startsWith("queue:")) {
            return session.createQueue(physical.substring("queue:".length()));
        }
        if (physical.startsWith("topic:")) {
            return session.createTopic(physical.substring("topic:".length()));
        }
        return session.createTopic(physical);
    }

    /**
     * \u4ece JMS Message \u63d0\u53d6 payload \u5b57\u7b26\u4e32\uff08\u517c\u5bb9 BytesMessage / TextMessage\uff09\u3002
     *
     * @param message JMS \u6d88\u606f
     * @return \u6d88\u606f\u6587\u672c
     * @throws JMSException \u8bfb\u53d6\u6d88\u606f\u5931\u8d25\u65f6\u629b\u51fa
     */
    public static String extractPayload(Message message) throws JMSException {
        if (message instanceof BytesMessage bm) {
            bm.reset();
            byte[] buf = new byte[(int) bm.getBodyLength()];
            bm.readBytes(buf);
            return new String(buf, StandardCharsets.UTF_8);
        }
        if (message instanceof TextMessage tm) {
            return tm.getText();
        }
        return "";
    }

    /**
     * \u5b89\u5168\u8bfb\u53d6 JMS String \u5c5e\u6027\uff08\u5f02\u5e38\u8fd4\u56de null\uff09\u3002
     *
     * @param message JMS \u6d88\u606f
     * @param key \u5c5e\u6027\u540d
     * @return \u5c5e\u6027\u503c\uff1b\u8bfb\u53d6\u5931\u8d25\u65f6\u8fd4\u56de {@code null}
     */
    public static String stringProperty(Message message, String key) {
        try {
            return message.getStringProperty(key);
        } catch (JMSException ignore) {
            return null;
        }
    }

    /**
     * \u8bfb\u53d6 JMS MessageID\uff0c\u5f02\u5e38\u8fd4\u56de null\u3002
     *
     * @param message JMS \u6d88\u606f
     * @return \u6d88\u606f\u6807\u8bc6\uff1b\u8bfb\u53d6\u5931\u8d25\u65f6\u8fd4\u56de {@code null}
     */
    public static String messageIdOf(Message message) {
        try {
            return message.getJMSMessageID();
        } catch (JMSException e) {
            return null;
        }
    }

    /**
     * \u8bfb\u53d6 JMS CorrelationID\uff0c\u5f02\u5e38\u8fd4\u56de null\u3002
     *
     * @param message JMS \u6d88\u606f
     * @return \u5173\u8054\u6807\u8bc6\uff1b\u8bfb\u53d6\u5931\u8d25\u65f6\u8fd4\u56de {@code null}
     */
    public static String correlationIdOf(Message message) {
        try {
            return message.getJMSCorrelationID();
        } catch (JMSException e) {
            return null;
        }
    }

    /**
     * \u628a JMS MessageID \u54c8\u5e0c\u4e3a long \u6295\u9012\u6807\u7b7e\uff0c\u5f02\u5e38\u8fd4\u56de 0\u3002
     *
     * @param message JMS \u6d88\u606f
     * @return \u6d88\u606f\u6807\u8bc6\u7684\u54c8\u5e0c\u503c\uff1b\u8bfb\u53d6\u5931\u8d25\u65f6\u8fd4\u56de 0
     */
    public static long messageIdHash(Message message) {
        try {
            String id = message.getJMSMessageID();
            return Objects.isNull(id) ? 0L : (long) id.hashCode();
        } catch (JMSException e) {
            return 0L;
        }
    }
}
