package io.ddd4j.mq.activemq;

import io.ddd4j.mq.event.MQEvent;
import io.ddd4j.mq.delivery.MQDeliveryHeaders;
import io.ddd4j.mq.message.Acknowledgment;
import io.ddd4j.mq.event.MQEventSerialization;
import io.ddd4j.mq.listener.MQListener;
import io.ddd4j.mq.message.MessageHeaders;
import io.ddd4j.mq.serialization.JsonMQEventSerialization;
import org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import jakarta.jms.Connection;
import jakarta.jms.BytesMessage;
import jakarta.jms.Message;
import jakarta.jms.MessageConsumer;
import jakarta.jms.MessageProducer;
import jakarta.jms.Session;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 真实 Artemis Broker 上验证 JMS 适配器的发布、重投与生命周期。
 */
@Testcontainers(disabledWithoutDocker = true)
class ArtemisContainerIntegrationTest {

    @Container
    static final GenericContainer<?> ARTEMIS = new GenericContainer<>(
            DockerImageName.parse("apache/activemq-artemis:2.44.0-alpine"))
            .withEnv("ARTEMIS_USER", "ddd4j")
            .withEnv("ARTEMIS_PASSWORD", "ddd4j-test")
            .withTmpFs(Collections.singletonMap("/var/lib/artemis-instance", "rw,exec,size=512m,uid=1001,gid=1001"))
            .withExposedPorts(61616)
            .waitingFor(Wait.forLogMessage(".*AMQ221007: Server is now active.*", 1))
            .withStartupTimeout(Duration.ofMinutes(2));

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

    public static class FailsOnceListener {
        private final AtomicInteger attempts = new AtomicInteger();
        private final CountDownLatch secondAttempt = new CountDownLatch(1);
        private volatile String messageId;

        public void handle(MQEvent event) {
            messageId = event.getMsgId();
            if (attempts.incrementAndGet() == 1) {
                throw new IllegalStateException("retry once");
            }
            secondAttempt.countDown();
        }
    }

    public static class CausalListener {
        private final LinkedBlockingQueue<MQEvent> events = new LinkedBlockingQueue<>();

        public void handle(MQEvent event) {
            events.add(event);
        }
    }
}
