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
package io.ddd4j.mq.rocketmq;

import io.ddd4j.core.context.BaseContext;
import io.ddd4j.mq.event.MQEvent;
import io.ddd4j.mq.listener.MQListener;
import io.ddd4j.mq.message.Acknowledgment;
import io.ddd4j.mq.serialization.JsonMQEventSerialization;
import org.apache.rocketmq.common.message.MessageExt;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.FixedHostPortGenericContainer;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.Container;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.images.builder.Transferable;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.net.InetAddress;
import java.net.ServerSocket;
import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 使用真实 RocketMQ NameServer/Broker 验证命名空间物理 topic 与原生因果属性。 */
@Testcontainers(disabledWithoutDocker = true)
class RocketMQNativeIntegrationTest {

    private static final String IMAGE = "apache/rocketmq:5.3.2";
    private static final String TOPIC = "tenant_orders";
    private static Network network;
    private static GenericContainer<?> nameServer;
    private static FixedHostPortGenericContainer<?> broker;

    @BeforeAll
    static void startBroker() throws Exception {
        network = Network.newNetwork();
        nameServer = new GenericContainer<>(IMAGE)
                .withNetwork(network)
                .withNetworkAliases("namesrv")
                .withExposedPorts(9876)
                .withCommand("sh", "mqnamesrv")
                .waitingFor(Wait.forLogMessage(".*Name Server boot success.*", 1));
        nameServer.start();

        int brokerPort;
        try (ServerSocket socket = new ServerSocket(0)) {
            brokerPort = socket.getLocalPort();
        }
        String brokerIp = InetAddress.getByName(nameServer.getHost()).getHostAddress();
        String config = "brokerClusterName=DefaultCluster\n"
                + "brokerName=ddd4j-broker\n"
                + "brokerId=0\n"
                + "brokerIP1=" + brokerIp + "\n"
                + "listenPort=" + brokerPort + "\n"
                + "autoCreateTopicEnable=true\n"
                + "brokerRole=ASYNC_MASTER\n"
                + "flushDiskType=ASYNC_FLUSH\n";
        broker = new FixedHostPortGenericContainer<>(IMAGE);
        broker.withNetwork(network)
                .withEnv("NAMESRV_ADDR", "namesrv:9876")
                .withCopyToContainer(Transferable.of(config), "/tmp/ddd4j-broker.conf")
                .withFixedExposedPort(brokerPort, brokerPort)
                .withCommand("sh", "mqbroker", "-c", "/tmp/ddd4j-broker.conf")
                .waitingFor(Wait.forLogMessage(".*boot success.*", 1));
        broker.start();
        boolean registered = false;
        for (int attempt = 0; attempt < 30; attempt++) {
            Container.ExecResult cluster = broker.execInContainer("sh", "mqadmin", "clusterList",
                    "-n", "namesrv:9876");
            if (cluster.getStdout().contains("ddd4j-broker")) {
                registered = true;
                break;
            }
            Thread.sleep(1_000L);
        }
        assertTrue(registered, "Broker must register with the NameServer");
        Container.ExecResult created = broker.execInContainer("sh", "mqadmin", "updateTopic",
                "-n", "namesrv:9876", "-c", "DefaultCluster", "-t", TOPIC);
        assertEquals(0, created.getExitCode(), created.getStdout() + created.getStderr());
        assertTrue(created.getStdout().contains("create topic"), created.getStdout() + created.getStderr());
    }

    @AfterAll
    static void stopBroker() {
        if (broker != null) {
            broker.stop();
        }
        if (nameServer != null) {
            nameServer.stop();
        }
        if (network != null) {
            network.close();
        }
    }

    @AfterEach
    void clearContext() {
        BaseContext.clear();
    }

    @Test
    void namespacedTaggedMessageRoundTripsWithNativeHeaders() throws Exception {
        String nameServerAddress = nameServer.getHost() + ":" + nameServer.getMappedPort(9876);
        RocketMQProperties properties = new RocketMQProperties();
        properties.setNameServer(nameServerAddress);
        properties.setBroker("rocket");
        properties.setEnabled(true);
        properties.setNamespace("tenant");
        properties.setProducerGroup("ddd4j-producer-" + UUID.randomUUID());
        CountDownLatch delivered = new CountDownLatch(1);
        AtomicReference<MQEvent> received = new AtomicReference<>();
        AtomicReference<MessageExt> nativeMessage = new AtomicReference<>();
        RocketMQClient client = new RocketMQClient(properties) {
            @Override
            public void consume(MQListener listener, MQEvent event, Acknowledgment acknowledgment) {
                received.set(event);
                nativeMessage.set(acknowledgment.unwrap(MessageExt.class).orElse(null));
                acknowledgment.ack();
                delivered.countDown();
            }
        };
        try {
            MQListener listener = MQListener.builder()
                    .group("ddd4j-consumer-" + UUID.randomUUID())
                    .namespace("tenant").topic("orders").tags("priority").build();
            client.init(Collections.singletonList(listener), properties, new JsonMQEventSerialization(), null);
            MQEvent event = new MQEvent();
            event.setNamespace("tenant");
            event.setTopic("orders");
            event.setTag("priority");
            event.setMsgId("rocket-" + UUID.randomUUID());
            event.setCorrelationId("correlation-" + UUID.randomUUID());
            event.setCausationId("causation-" + UUID.randomUUID());
            event.publish();

            assertTrue(delivered.await(30, TimeUnit.SECONDS), "RocketMQ must deliver the namespaced topic");
            assertNotNull(nativeMessage.get());
            assertEquals(TOPIC, nativeMessage.get().getTopic());
            assertEquals("priority", nativeMessage.get().getTags());
            assertEquals(event.getMsgId(), received.get().getMsgId());
            assertEquals(event.getCorrelationId(), received.get().getCorrelationId());
            assertEquals(event.getCausationId(), received.get().getCausationId());
        } finally {
            client.close();
        }
    }

    @Test
    void failedListenerIsRedeliveredByNativeBroker() throws Exception {
        String nameServerAddress = nameServer.getHost() + ":" + nameServer.getMappedPort(9876);
        RocketMQProperties properties = new RocketMQProperties();
        properties.setNameServer(nameServerAddress);
        properties.setBroker("rocket");
        properties.setEnabled(true);
        properties.setNamespace("tenant");
        properties.setProducerGroup("ddd4j-retry-producer-" + UUID.randomUUID());
        AtomicInteger attempts = new AtomicInteger();
        CountDownLatch retried = new CountDownLatch(1);
        AtomicReference<String> receivedMessageId = new AtomicReference<>();
        RocketMQClient client = new RocketMQClient(properties) {
            @Override
            public void consume(MQListener listener, MQEvent event, Acknowledgment acknowledgment) {
                if (attempts.incrementAndGet() == 1) {
                    throw new IllegalStateException("first delivery fails");
                }
                receivedMessageId.set(event.getMsgId());
                acknowledgment.ack();
                retried.countDown();
            }
        };
        try {
            MQListener listener = MQListener.builder()
                    .group("ddd4j-retry-consumer-" + UUID.randomUUID())
                    .namespace("tenant").topic("orders").tags("retry").build();
            client.init(Collections.singletonList(listener), properties, new JsonMQEventSerialization(), null);
            MQEvent event = new MQEvent();
            event.setNamespace("tenant");
            event.setTopic("orders");
            event.setTag("retry");
            event.setMsgId("rocket-retry-" + UUID.randomUUID());
            event.publish();

            assertTrue(retried.await(90, TimeUnit.SECONDS), "Broker must redeliver after listener failure");
            assertEquals(2, attempts.get());
            assertEquals(event.getMsgId(), receivedMessageId.get());
        } finally {
            client.close();
        }
    }
}
