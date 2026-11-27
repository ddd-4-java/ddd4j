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
import org.testcontainers.containers.Container;
import org.testcontainers.containers.FixedHostPortGenericContainer;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.images.builder.Transferable;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.net.InetAddress;
import java.net.ServerSocket;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 使用真实 RocketMQ NameServer/Broker 验证命名空间物理 topic 与原生因果属性。
 */
@Testcontainers(disabledWithoutDocker = true)
class RocketMQNativeIntegrationTest {

    /** \u6d4b\u8bd5\u4f7f\u7528\u7684\u5bb9\u5668\u955c\u50cf\u6216\u56fe\u50cf\u6837\u672c\u3002 */
    private static final String IMAGE = "apache/rocketmq:5.3.2";
    /** \u6784\u9020\u672c\u7ec4\u6d4b\u8bd5\u8f93\u5165\u65f6\u590d\u7528\u7684\u56fa\u5b9a\u6807\u8bc6\u6216\u540d\u79f0\u3002 */
    private static final String TOPIC = "tenant_orders";
    /** \u4f9b\u6d4b\u8bd5\u8bfb\u53d6\u6216\u7ea6\u675f\u7f51\u7edc\u4fe1\u606f\u7684\u5939\u5177\u3002 */
    private static Network network;
    /** \u6d4b\u8bd5\u5ba2\u6237\u7aef\u8fde\u63a5\u7684\u6d88\u606f\u4ee3\u7406\u540d\u79f0\u670d\u52a1\u5730\u5740\u3002 */
    private static GenericContainer<?> nameServer;
    /** \u7528\u4e8e\u9a8c\u8bc1\u6d88\u606f\u6295\u9012\u8def\u5f84\u7684\u903b\u8f91\u4ee3\u7406\u3002 */
    private static FixedHostPortGenericContainer<?> broker;

    /**
     * \u6d4b\u8bd5\u6267\u884c\u524d\u6267\u884c <code>Network.newNetwork()</code>\u3001<code>Wait.forLogMessage()</code>\u3001<code>nameServer.start()</code>\u3001<code>socket.getLocalPort()</code> \u5b8c\u6210\u8d44\u6e90\u51c6\u5907\u3002
     * @throws Exception \u5f53\u524d\u7528\u4f8b\u51c6\u5907\u8d44\u6e90\u6216\u8c03\u7528\u6d4b\u8bd5\u5165\u53e3\u5931\u8d25
     */
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
                .withTmpFs(Map.of("/home/rocketmq/store", "rw,size=2048m,uid=3000,gid=3000,mode=0700",
                        "/home/rocketmq/logs", "rw,size=128m,uid=3000,gid=3000,mode=0700"))
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

    /** \u6d4b\u8bd5\u7ed3\u675f\u540e\u6267\u884c <code>broker.stop()</code>\u3001<code>nameServer.stop()</code>\u3001<code>network.close()</code> \u5b8c\u6210\u8d44\u6e90\u56de\u6536\u6216\u72b6\u6001\u6062\u590d\u3002 */
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

    /** \u6d4b\u8bd5\u7ed3\u675f\u540e\u6267\u884c <code>BaseContext.clear()</code> \u5b8c\u6210\u8d44\u6e90\u56de\u6536\u6216\u72b6\u6001\u6062\u590d\u3002 */
    @AfterEach
    void clearContext() {
        BaseContext.clear();
    }

    /**
     * \u9a8c\u8bc1<code>delivered.await(30, TimeUnit.SECONDS)</code>\u4e3a\u771f\uff0c<code>nativeMessage.get()</code>\u975e\u7a7a\uff0c<code>received.get().getCausationId()</code> \u7b49\u4e8e <code>event.getCausationId()</code>\u3002
     * @throws Exception \u7b49\u5f85\u5e76\u53d1\u64cd\u4f5c\u65f6\u5931\u8d25
     */
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

    /**
     * \u9a8c\u8bc1<code>retried.await(90, TimeUnit.SECONDS)</code>\u4e3a\u771f\uff0c<code>attempts.get()</code> \u7b49\u4e8e <code>2</code>\uff0c<code>receivedMessageId.get()</code> \u7b49\u4e8e <code>event.getMsgId()</code>\u3002
     * @throws Exception \u7b49\u5f85\u5e76\u53d1\u64cd\u4f5c\u65f6\u5931\u8d25
     */
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
