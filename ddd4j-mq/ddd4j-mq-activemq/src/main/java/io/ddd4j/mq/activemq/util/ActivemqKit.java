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
     * 根据物理地址前缀创建 JMS Destination。
     * @param session 会话
     * @param physical 字符串参数
     * @return 创建的Destination
     * @throws javax.jms.JMSException 执行对应操作失败时抛出
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
     * 从 JMS Message 提取 payload 字符串（兼容 BytesMessage / TextMessage）。
     * @param message 消息内容
     * @return 提取的字符串内容
     * @throws javax.jms.JMSException 执行对应操作失败时抛出
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

    private static boolean hasText(String s) {
        return StrKit.isNotEmpty(s);
    }
}
