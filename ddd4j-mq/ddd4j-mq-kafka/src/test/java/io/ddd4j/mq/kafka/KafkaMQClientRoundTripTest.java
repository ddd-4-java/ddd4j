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
package io.ddd4j.mq.kafka;

import io.ddd4j.mq.MQProperties;
import io.ddd4j.core.context.BaseContext;
import org.apache.kafka.clients.admin.AdminClient;
import io.ddd4j.mq.annotation.MQEventListener;
import io.ddd4j.mq.event.MQEvent;
import io.ddd4j.mq.listener.MQListener;
import io.ddd4j.mq.serialization.JsonMQEventSerialization;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

import java.lang.reflect.Method;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link KafkaMQClient} 的 Kafka 集成测试。
 *
 * <p>使用 Testcontainers Kafka 模块（org.testcontainers:testcontainers-kafka，
 * 模块清单来源 <a href="https://testcontainers.com/modules/">testcontainers.com/modules</a>）
 * 启动真实 Kafka broker，验证"生产 → 序列化 → broker → 消费 → 反序列化 → 监听器回调"全链路。
 *
 * <p>需要本地 Docker 可用；无 Docker 环境时 Testcontainers 会自动跳过。
 */
@Testcontainers(disabledWithoutDocker = true)
class KafkaMQClientRoundTripTest {

    @Container
    private static final KafkaContainer KAFKA = new KafkaContainer(
            DockerImageName.parse("apache/kafka:3.9.1"));

    @Test
    @DisplayName("生产/消费往返：事件经 Kafka broker 送达监听器且负载完整")
    void produceConsumeRoundTrip() throws Exception {
        // 1. 配置：namespace + bootstrapServers 指向容器
        KafkaMQProperties kafkaProps = new KafkaMQProperties();
        kafkaProps.setBootstrapServers(KAFKA.getBootstrapServers());
        kafkaProps.setAutoCreateTopics(true);
        kafkaProps.setGroupIdPrefix("billing");

        MQProperties mqProps = new MQProperties();
        mqProps.setEnabled(true);
        mqProps.setBroker("kafka");
        mqProps.setNamespace("it");
        mqProps.setDefaultTopic("ORDER");

        KafkaMQClient client = new KafkaMQClient(kafkaProps, null);

        // 2. 监听器：注解元数据 + 记录回调
        OrderListenerBean bean = new OrderListenerBean();
        Method method = OrderListenerBean.class.getMethod("onPaid", OrderPaidEvent.class);
        MQListener listener = MQListener.of(bean, method,
                method.getAnnotation(MQEventListener.class));

        // 3. 走框架统一入口 init()：注册序列化器/配置到 BaseContext，
        //    初始化生产者（注册进 publishers Map）并注册消费者
        try {
            client.init(List.of(listener), mqProps, new JsonMQEventSerialization(), null);

            // 4. 生产：走 MQEvent.publish() 的 BaseContext 路由（与生产环境用法一致）
            OrderPaidEvent event = new OrderPaidEvent();
            event.setTopic("ORDER");
            event.setTag("paid");
            event.setTenantId("t-100");
            event.orderId = "o-12345";
            event.amount = 9900L;
            event.publish();

            // 5. 断言：监听器在 30s 内收到完整负载
            assertThat(bean.latch.await(30, TimeUnit.SECONDS))
                    .as("监听器应在 30 秒内收到事件")
                    .isTrue();
            OrderPaidEvent received = bean.received.get();
            assertThat(received).isNotNull();
            assertThat(received.orderId).isEqualTo("o-12345");
            assertThat(received.amount).isEqualTo(9900L);
            assertThat(received.getTenantId()).isEqualTo("t-100");
            assertThat(received.getMsgId()).isEqualTo(event.getMsgId());
            // 真实 broker 上的组身份及离组结果，证明前缀配置与 consumer.close 均已生效。
            try (AdminClient admin = AdminClient.create(kafkaProps.adminProperties())) {
                assertThat(admin.describeConsumerGroups(java.util.Collections.singletonList("billing-onPaid"))
                        .all().get(10, TimeUnit.SECONDS).get("billing-onPaid").members()).hasSize(1);
                client.close();
                client.close();
                assertThat(admin.describeConsumerGroups(java.util.Collections.singletonList("billing-onPaid"))
                        .all().get(10, TimeUnit.SECONDS).get("billing-onPaid").members()).isEmpty();
            }
        } finally {
            client.close();
            BaseContext.clear();
        }
    }

    /**
     * 测试事件：订单已支付（Jackson 需要无参构造 + 公共字段/访问器）。
     */
    public static class OrderPaidEvent extends MQEvent {
        public String orderId;
        public Long amount;
    }

    /**
     * 监听 Bean：@MQEventListener 注解驱动，收到事件后放行 latch。
     */
    public static class OrderListenerBean {
        final CountDownLatch latch = new CountDownLatch(1);
        final AtomicReference<OrderPaidEvent> received = new AtomicReference<>();

        @MQEventListener(topic = "ORDER", tags = "paid")
        public void onPaid(OrderPaidEvent event) {
            received.set(event);
            latch.countDown();
        }
    }
}
