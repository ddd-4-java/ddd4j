package io.ddd4j.mq.activemq;

import io.ddd4j.mq.delivery.MQDeliveryHeaders;
import io.ddd4j.mq.event.MQEvent;
import io.ddd4j.mq.event.MQEventSerialization;
import io.ddd4j.mq.listener.MQListener;
import io.ddd4j.mq.message.Acknowledgment;
import io.ddd4j.mq.message.MessageHeaders;
import io.ddd4j.mq.serialization.JsonMQEventSerialization;
import jakarta.jms.*;
import org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.lang.IllegalStateException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 真实 Artemis Broker 上验证 JMS 适配器的发布、重投与生命周期。
 */
@Testcontainers(disabledWithoutDocker = true)
class ArtemisContainerIntegrationTest {

    /** \u4e3a\u96c6\u6210\u6d4b\u8bd5\u63d0\u4f9b\u9694\u79bb <code>Generic</code> \u670d\u52a1\u7684\u5bb9\u5668\u3002 */
    @Container
    static final GenericContainer<?> ARTEMIS = new GenericContainer<>(
            DockerImageName.parse("apache/activemq-artemis:2.44.0-alpine"))
            .withEnv("ARTEMIS_USER", "ddd4j")
            .withEnv("ARTEMIS_PASSWORD", "ddd4j-test")
            .withTmpFs(Collections.singletonMap("/var/lib/artemis-instance", "rw,exec,size=512m,uid=1001,gid=1001"))
            .withExposedPorts(61616)
            .waitingFor(Wait.forLogMessage(".*AMQ221007: Server is now active.*", 1))
            .withStartupTimeout(Duration.ofMinutes(2));

    /**
     * \u9a8c\u8bc1<code>client.initConsumer(listener, properties)</code>\u4e3a\u771f\uff0c<code>raw</code>\u975e\u7a7a\uff0c<code>lastAcknowledgment.get().correlationId()</code> \u7b49\u4e8e <code>"native-correlation"</code>\u3002
     * @throws Exception \u53d1\u8d77\u6d4b\u8bd5\u8bf7\u6c42\u65f6\u5931\u8d25
     */
    @Test
    void nativeJmsCausalPropertiesShouldRoundTripThroughClient() throws Exception {
        String suffix = UUID.randomUUID().toString().replace("-", "");
        String topic = "causality" + suffix;
        ActiveMQProperties properties = new ActiveMQProperties();
        properties.setBrokerUrl("tcp://" + ARTEMIS.getHost() + ":" + ARTEMIS.getMappedPort(61616));
        properties.setUsername("ddd4j");
        properties.setPassword("ddd4j-test");
        ActiveMQConnectionFactory observerFactory = new ActiveMQConnectionFactory(properties.getBrokerUrl());
        observerFactory.setUser(properties.getUsername());
        observerFactory.setPassword(properties.getPassword());
        Connection observerConnection = observerFactory.createConnection();
        Session observerSession = observerConnection.createSession(false, Session.AUTO_ACKNOWLEDGE);
        MessageConsumer observer = observerSession.createConsumer(observerSession.createTopic("ddd4j." + topic));
        observerConnection.start();
        AtomicReference<Acknowledgment> lastAcknowledgment = new AtomicReference<>();
        ActiveMQClient client = new ActiveMQClient(properties) {
            @Override
            public MQEventSerialization serialization() {
                return new JsonMQEventSerialization();
            }

            @Override
            public void consume(MQListener listener, MQEvent event, Acknowledgment ack) throws Throwable {
                lastAcknowledgment.set(ack);
                super.consume(listener, event, ack);
            }
        };
        CausalListener bean = new CausalListener();
        MQListener listener = new MQListener(bean,
                CausalListener.class.getMethod("handle", MQEvent.class),
                "ddd4j_group_" + suffix, "ddd4j", topic, "*",
                Collections.singletonList("*"), ".");

        try {
            assertTrue(client.initConsumer(listener, properties));
            java.util.function.Consumer<MQEvent> producer = client.initProducer(properties);
            for (int i = 0; i < 3; i++) {
                MQEvent event = new MQEvent();
                event.setMsgId("causality-" + suffix + "-" + i);
                event.setNamespace("ddd4j");
                event.setTopic(topic);
                if (i >= 1) {
                    event.setCorrelationId("correlation-" + suffix);
                }
                if (i == 2) {
                    event.setCausationId("cause-" + suffix);
                }
                producer.accept(event);

                Message raw = observer.receive(10_000);
                assertNotNull(raw);
                assertEquals(event.getMsgId(), raw.getStringProperty(
                        ActiveMQClient.jmsProperty(MQDeliveryHeaders.MESSAGE_ID)));
                assertEquals(event.getCorrelationId(), raw.getStringProperty(
                        ActiveMQClient.jmsProperty(MQDeliveryHeaders.CORRELATION_ID)));
                assertEquals(event.getCausationId(), raw.getStringProperty(
                        ActiveMQClient.jmsProperty(MQDeliveryHeaders.CAUSATION_ID)));
                if (i == 0) {
                    assertNull(raw.getJMSCorrelationID());
                } else {
                    assertEquals(event.getCorrelationId(), raw.getJMSCorrelationID());
                }

                MQEvent consumed = bean.events.poll(10, TimeUnit.SECONDS);
                assertNotNull(consumed);
                assertEquals(event.getMsgId(), consumed.getMsgId());
                assertEquals(event.getCorrelationId(), consumed.getCorrelationId());
                assertEquals(event.getCausationId(), consumed.getCausationId());
                assertEquals(event.getCorrelationId(), lastAcknowledgment.get().correlationId());
            }

            MQEvent payloadEvent = new MQEvent();
            payloadEvent.setMsgId("payload-id");
            payloadEvent.setNamespace("ddd4j");
            payloadEvent.setTopic(topic);
            BytesMessage nativeMessage = observerSession.createBytesMessage();
            String payload = new JsonMQEventSerialization().serialize(payloadEvent);
            nativeMessage.writeBytes(payload.getBytes(StandardCharsets.UTF_8));
            nativeMessage.setStringProperty(ActiveMQClient.jmsProperty(MQDeliveryHeaders.MESSAGE_ID), "native-id");
            nativeMessage.setStringProperty(ActiveMQClient.jmsProperty(MQDeliveryHeaders.CORRELATION_ID), "native-correlation");
            nativeMessage.setStringProperty(ActiveMQClient.jmsProperty(MQDeliveryHeaders.CAUSATION_ID), "native-cause");
            nativeMessage.setJMSCorrelationID("legacy-correlation");
            try (MessageProducer nativeProducer = observerSession.createProducer(observerSession.createTopic("ddd4j." + topic))) {
                nativeProducer.send(nativeMessage);
            }
            assertNotNull(observer.receive(10_000));
            MQEvent nativeConsumed = bean.events.poll(10, TimeUnit.SECONDS);
            assertNotNull(nativeConsumed);
            assertEquals("native-id", nativeConsumed.getMsgId());
            assertEquals("native-correlation", nativeConsumed.getCorrelationId());
            assertEquals("native-cause", nativeConsumed.getCausationId());
            assertEquals("native-correlation", lastAcknowledgment.get().correlationId());
        } finally {
            try {
                client.close();
            } finally {
                observer.close();
                observerSession.close();
                observerConnection.close();
                observerFactory.close();
            }
        }
    }

    /**
     * \u9a8c\u8bc1<code>client.initConsumer(listener, properties)</code>\u4e3a\u771f\uff0c<code>brokerMessage</code>\u975e\u7a7a\uff0c<code>client.initProducer(properties).accept(afterClose)</code> \u629b\u51fa <code>RuntimeException.class</code>\u3002
     * @throws Exception \u7b49\u5f85\u5e76\u53d1\u64cd\u4f5c\u65f6\u5931\u8d25
     */
    @Test
    void failedHandlerIsRedeliveredAndPublishedMessageKeepsItsId() throws Exception {
        String suffix = UUID.randomUUID().toString().replace("-", "");
        String topic = "orders" + suffix;
        String messageId = "artemis-" + suffix;
        ActiveMQProperties properties = new ActiveMQProperties();
        properties.setBrokerUrl("tcp://" + ARTEMIS.getHost() + ":" + ARTEMIS.getMappedPort(61616));
        properties.setUsername("ddd4j");
        properties.setPassword("ddd4j-test");
        ActiveMQConnectionFactory observerFactory = new ActiveMQConnectionFactory(properties.getBrokerUrl());
        observerFactory.setUser(properties.getUsername());
        observerFactory.setPassword(properties.getPassword());
        Connection observerConnection = observerFactory.createConnection();
        Session observerSession = observerConnection.createSession(false, Session.AUTO_ACKNOWLEDGE);
        MessageConsumer observer = observerSession.createConsumer(observerSession.createTopic("ddd4j." + topic));
        observerConnection.start();
        ActiveMQClient client = new ActiveMQClient(properties) {
            @Override
            public MQEventSerialization serialization() {
                return new JsonMQEventSerialization();
            }
        };
        FailsOnceListener bean = new FailsOnceListener();
        MQListener listener = new MQListener(bean,
                FailsOnceListener.class.getMethod("handle", MQEvent.class),
                "ddd4j_group_" + suffix, "ddd4j", topic, "*",
                Collections.singletonList("*"), ".");

        try {
            assertTrue(client.initConsumer(listener, properties));
            MQEvent event = new MQEvent();
            event.setMsgId(messageId);
            event.setNamespace("ddd4j");
            event.setTopic(topic);
            client.initProducer(properties).accept(event);

            Message brokerMessage = observer.receive(10_000);
            assertNotNull(brokerMessage);
            assertEquals(messageId, brokerMessage.getStringProperty(
                    ActiveMQClient.jmsProperty(MessageHeaders.HEADER_MESSAGE_ID)));
            assertTrue(bean.secondAttempt.await(10, TimeUnit.SECONDS));
            assertEquals(2, bean.attempts.get());
            assertEquals(messageId, bean.messageId);
        } finally {
            try {
                client.close();
            } finally {
                observer.close();
                observerSession.close();
                observerConnection.close();
                observerFactory.close();
            }
        }

        MQEvent afterClose = new MQEvent();
        afterClose.setMsgId("after-close-" + suffix);
        afterClose.setNamespace("ddd4j");
        afterClose.setTopic(topic);
        assertThrows(RuntimeException.class, () -> client.initProducer(properties).accept(afterClose));
    }

    /** \u5c06 <code>attempts</code>\u3001<code>secondAttempt</code>\u3001<code>messageId</code> \u7b49\u72b6\u6001\u4e00\u8d77\u4f20\u5165\u5f53\u524d\u6d4b\u8bd5\uff0c\u4f9b\u8c03\u7528\u6216\u7ed3\u679c\u65ad\u8a00\u4f7f\u7528\u3002 */
    public static class FailsOnceListener {
        /** \u8ddf\u8e2a\u6d4b\u8bd5\u64cd\u4f5c\u7684\u8c03\u7528\u6216\u91cd\u8bd5\u6b21\u6570\u3002 */
        private final AtomicInteger attempts = new AtomicInteger();
        /** \u534f\u8c03\u6d4b\u8bd5\u7ebf\u7a0b\u5728\u6307\u5b9a\u6267\u884c\u9636\u6bb5\u7b49\u5f85\u6216\u7ee7\u7eed\u7684\u95e8\u95e9\u3002 */
        private final CountDownLatch secondAttempt = new CountDownLatch(1);
        /** \u6d88\u606f\u6807\u8bc6\u3002 */
        private volatile String messageId;

        /**
         * \u6839\u636e\u672c\u6b21\u8c03\u7528\u66f4\u65b0 <code>messageId = event.getMsgId()</code>\uff0c\u7528\u4e8e\u540e\u7eed\u6d4b\u8bd5\u6b65\u9aa4\u3002
         * @param event \u4ea4\u7ed9\u88ab\u6d4b\u5165\u53e3\u5904\u7406\u7684\u6d4b\u8bd5\u9886\u57df\u4e8b\u4ef6
         */
        public void handle(MQEvent event) {
            messageId = event.getMsgId();
            if (attempts.incrementAndGet() == 1) {
                throw new IllegalStateException("retry once");
            }
            secondAttempt.countDown();
        }
    }

    /** \u5c06 <code>events</code> \u7b49\u72b6\u6001\u4e00\u8d77\u4f20\u5165\u5f53\u524d\u6d4b\u8bd5\uff0c\u4f9b\u8c03\u7528\u6216\u7ed3\u679c\u65ad\u8a00\u4f7f\u7528\u3002 */
    public static class CausalListener {
        /** \u5f53\u524d\u6d4b\u8bd5\u671f\u95f4\u6536\u96c6\u7684\u9886\u57df\u4e8b\u4ef6\uff0c\u4f9b\u65ad\u8a00\u6bd4\u8f83\u53d1\u5e03\u5185\u5bb9\u6216\u987a\u5e8f\u3002 */
        private final LinkedBlockingQueue<MQEvent> events = new LinkedBlockingQueue<>();

        /**
         * \u8c03\u7528 <code>events.add()</code> \u6267\u884c\u5f53\u524d\u5939\u5177\u64cd\u4f5c\u3002
         * @param event \u4ea4\u7ed9\u88ab\u6d4b\u5165\u53e3\u5904\u7406\u7684\u6d4b\u8bd5\u9886\u57df\u4e8b\u4ef6
         */
        public void handle(MQEvent event) {
            events.add(event);
        }
    }
}
