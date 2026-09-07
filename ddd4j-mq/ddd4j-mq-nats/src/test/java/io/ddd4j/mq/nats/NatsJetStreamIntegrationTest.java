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
package io.ddd4j.mq.nats;

import io.nats.client.Connection;
import io.nats.client.JetStreamManagement;
import io.nats.client.api.PublishAck;
import io.nats.client.api.StreamConfiguration;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import static org.assertj.core.api.Assertions.assertThat;

/** NATS JetStream 容器轨：验证 ddd4j 配置连接与持久化发布确认。 */
@Testcontainers(disabledWithoutDocker = true)
class NatsJetStreamIntegrationTest {

    @Container
    static final GenericContainer<?> NATS = new GenericContainer<>(
            DockerImageName.parse("nats:2.14.6-alpine"))
            .withCommand("-js")
            .withExposedPorts(4222)
            .waitingFor(Wait.forListeningPort())
            .withStartupTimeout(Duration.ofMinutes(2));

    @Test
    void natsPropertiesConnectsAndJetStreamAcknowledgesPublish() throws Exception {
        NatsProperties properties = new NatsProperties();
        properties.setServers("nats://" + NATS.getHost() + ":" + NATS.getMappedPort(4222));
        properties.setConnectionName("ddd4j-nats-it");
        properties.setConnectTimeoutMillis(10_000L);

        try (Connection connection = properties.connect()) {
            String suffix = Long.toUnsignedString(System.nanoTime());
            String stream = "DDD4J_" + suffix;
            String subject = "ddd4j.it.nats." + suffix;
            JetStreamManagement management = connection.jetStreamManagement();
            management.addStream(StreamConfiguration.builder().name(stream).subjects(subject).build());

            PublishAck ack = connection.jetStream().publish(subject,
                    "ddd4j-nats-payload".getBytes(StandardCharsets.UTF_8));

            assertThat(connection.getStatus()).isEqualTo(Connection.Status.CONNECTED);
            assertThat(ack.getStream()).isEqualTo(stream);
            assertThat(ack.getSeqno()).isEqualTo(1L);
        }
    }
}
