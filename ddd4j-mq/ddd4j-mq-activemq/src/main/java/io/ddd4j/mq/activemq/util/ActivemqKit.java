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

import io.ddd4j.kit.lang.StrKit;

import javax.jms.*;

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

    /** \u9650\u5236\u8c03\u7528\u65b9\u76f4\u63a5\u521b\u5efa <code>ActivemqKit</code> \u5b9e\u4f8b\u3002 */
    private ActivemqKit() {
    }

    /**
     * 拼接物理地址：{@code namespace.topic[.tag]}，
     * 支持 {@code queue:} / {@code topic:} 前缀强制类型。
     * @param namespace 命名空间
     * @param topic 消息主题
     * @param tag 标签
     * @return 解析的字符串内容
     */
    public static String resolvePhysical(String namespace, String topic, String tag) {
        String base = hasText(topic) ? topic : "ddd4j.default.topic";
        if (hasText(namespace)) {
            base = namespace + "." + base;
        }
        return hasText(tag) ? base + "." + tag : base;
    }

    /**
     * \u6839\u636e\u7269\u7406\u5730\u5740\u524d\u7f00\u521b\u5efa JMS Destination\u3002
     * @param session \u4f1a\u8bdd
     * @param physical \u5b57\u7b26\u4e32\u53c2\u6570
     * @return \u521b\u5efa\u7684Destination
     * @throws javax.jms.JMSException \u6267\u884c\u5bf9\u5e94\u64cd\u4f5c\u5931\u8d25\u65f6\u629b\u51fa
     *
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
     * @param message \u6d88\u606f\u5185\u5bb9
     * @return \u63d0\u53d6\u7684\u5b57\u7b26\u4e32\u5185\u5bb9
     * @throws javax.jms.JMSException \u6267\u884c\u5bf9\u5e94\u64cd\u4f5c\u5931\u8d25\u65f6\u629b\u51fa
     *
     * @throws JMSException \u8bfb\u53d6\u6d88\u606f\u5931\u8d25\u65f6\u629b\u51fa
     */
    public static String extractPayload(Message message) throws JMSException {
        if (message instanceof BytesMessage) {
            BytesMessage bm = (BytesMessage) message;
            bm.reset();
            byte[] buf = new byte[(int) bm.getBodyLength()];
            bm.readBytes(buf);
            return new String(buf, StandardCharsets.UTF_8);
        }
        if (message instanceof TextMessage) {
            TextMessage tm = (TextMessage) message;
            return tm.getText();
        }
        return "";
    }

    /**
     * 安全读取 JMS String 属性（异常返回 null）。
     * @param message 消息内容
     * @param key 键
     * @return 字符串形式的字符串内容
     */
    public static String stringProperty(Message message, String key) {
        try {
            return message.getStringProperty(key);
        } catch (JMSException ignore) {
            return null;
        }
    }

    /**
     * 读取 JMS MessageID，异常返回 null。
     * @param message 消息内容
     * @return 返回的字符串内容
     */
    public static String messageIdOf(Message message) {
        try {
            return message.getJMSMessageID();
        } catch (JMSException e) {
            return null;
        }
    }

    /**
     * 读取 JMS CorrelationID，异常返回 null。
     * @param message 消息内容
     * @return 返回的字符串内容
     */
    public static String correlationIdOf(Message message) {
        try {
            return message.getJMSCorrelationID();
        } catch (JMSException e) {
            return null;
        }
    }

    /**
     * 把 JMS MessageID 哈希为 long 投递标签，异常返回 0。
     * @param message 消息内容
     * @return 对应的长整型数值
     */
    public static long messageIdHash(Message message) {
        try {
            String id = message.getJMSMessageID();
            return Objects.isNull(id) ? 0L : (long) id.hashCode();
        } catch (JMSException e) {
            return 0L;
        }
    }

    /**
     * \u68c0\u67e5\u8f93\u5165\u662f\u5426\u5b58\u5728\u975e\u7a7a\u767d\u5b57\u7b26\u3002
     * @param s \u5f85\u68c0\u67e5\u7684\u5b57\u7b26\u4e32
     * @return \u542b\u6709\u6548\u6587\u672c\u65f6\u8fd4\u56de true
     */
    private static boolean hasText(String s) {
        return StrKit.isNotEmpty(s);
    }
}
