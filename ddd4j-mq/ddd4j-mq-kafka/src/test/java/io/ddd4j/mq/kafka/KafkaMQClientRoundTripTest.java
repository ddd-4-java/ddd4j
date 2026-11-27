package io.ddd4j.mq.kafka;

import io.ddd4j.mq.MQProperties;
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

    /** \u4e3a\u5f53\u524d\u96c6\u6210\u6d4b\u8bd5\u63d0\u4f9b\u72ec\u7acb Kafka \u670d\u52a1\u7684\u5bb9\u5668\u3002 */
    @Container
    private static final KafkaContainer KAFKA = new KafkaContainer(
            DockerImageName.parse("apache/kafka:3.9.1"));

    /**
     * \u751f\u4ea7/\u6d88\u8d39\u5f80\u8fd4\uff1a\u4e8b\u4ef6\u7ecf Kafka broker \u9001\u8fbe\u76d1\u542c\u5668\u4e14\u8d1f\u8f7d\u5b8c\u6574\u3002
     * @throws Exception \u7b49\u5f85\u5e76\u53d1\u64cd\u4f5c\u65f6\u5931\u8d25
     */
    @Test
    @DisplayName("生产/消费往返：事件经 Kafka broker 送达监听器且负载完整")
    void produceConsumeRoundTrip() throws Exception {
        // 1. 配置：namespace + bootstrapServers 指向容器
        KafkaMQProperties kafkaProps = new KafkaMQProperties();
        kafkaProps.setBootstrapServers(KAFKA.getBootstrapServers());
        kafkaProps.setAutoCreateTopics(true);

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
    }

    /**
     * 测试事件：订单已支付（Jackson 需要无参构造 + 公共字段/访问器）。
     */
    public static class OrderPaidEvent extends MQEvent {
        /** \u8ba2\u5355\u6807\u8bc6\u3002 */
        public String orderId;
        /** \u6d4b\u8bd5\u4ea4\u6613\u4f7f\u7528\u7684\u6570\u91cf\u6216\u91d1\u989d\u3002 */
        public Long amount;
    }

    /**
     * 监听 Bean：@MQEventListener 注解驱动，收到事件后放行 latch。
     */
    public static class OrderListenerBean {
        /** \u534f\u8c03\u6d4b\u8bd5\u7ebf\u7a0b\u5728\u6307\u5b9a\u6267\u884c\u9636\u6bb5\u7b49\u5f85\u6216\u7ee7\u7eed\u7684\u95e8\u95e9\u3002 */
        final CountDownLatch latch = new CountDownLatch(1);
        /** \u5f53\u524d\u6d4b\u8bd5\u63a5\u6536\u7684\u6570\u636e\uff0c\u4f9b\u65ad\u8a00\u6838\u5bf9\u6295\u9012\u5185\u5bb9\u6216\u6b21\u6570\u3002 */
        final AtomicReference<OrderPaidEvent> received = new AtomicReference<>();

        /**
         * \u8c03\u7528 <code>received.set()</code>\u3001<code>latch.countDown()</code> \u6267\u884c\u5f53\u524d\u5939\u5177\u64cd\u4f5c\u3002
         * @param event \u4ea4\u7ed9\u88ab\u6d4b\u5165\u53e3\u5904\u7406\u7684\u6d4b\u8bd5\u9886\u57df\u4e8b\u4ef6
         */
        @MQEventListener(topic = "ORDER", tags = "paid")
        public void onPaid(OrderPaidEvent event) {
            received.set(event);
            latch.countDown();
        }
    }
}
