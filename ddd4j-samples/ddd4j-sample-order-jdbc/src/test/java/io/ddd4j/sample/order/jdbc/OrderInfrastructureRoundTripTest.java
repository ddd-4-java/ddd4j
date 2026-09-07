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
package io.ddd4j.sample.order.jdbc;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.redis.testcontainers.RedisContainer;
import io.ddd4j.mq.delivery.MQDeliveryPolicy;
import io.ddd4j.mq.delivery.MQOutboxRecord;
import io.ddd4j.sample.order.application.AddOrderLineCommand;
import io.ddd4j.sample.order.application.CreateOrderCommand;
import io.ddd4j.sample.order.application.OrderApplicationService;
import io.ddd4j.sample.order.application.OrderReadModel;
import io.ddd4j.sample.order.application.OutboxDispatchResult;
import io.ddd4j.sample.order.application.OutboxPublisher;
import io.ddd4j.sample.order.domain.Order;
import io.ddd4j.sample.order.domain.OrderQuery;
import io.ddd4j.sample.order.domain.OrderStatus;
import io.ddd4j.sample.order.kafka.KafkaIntegrationEventPublisher;
import io.ddd4j.sample.order.redis.RedisIdempotencyPort;
import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;
import redis.clients.jedis.JedisPooled;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 共享订单业务内核的真实基础设施闭环测试。
 *
 * <p>使用 PostgreSQL、Redis 与 Kafka 容器验证同一订单事务内的写模型、读模型和 Outbox，及其后续的
 * broker ACK 发布与支付幂等。该门禁要求 Docker 可用，不允许静默跳过。
 */
@Testcontainers
class OrderInfrastructureRoundTripTest {

    private static final String TOPIC = "ddd4j-sample-order-it";

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("postgres:16-alpine"));

    @Container
    private static final RedisContainer REDIS = new RedisContainer(
            DockerImageName.parse("redis:7.2-alpine"));

    @Container
    private static final KafkaContainer KAFKA = new KafkaContainer(
            DockerImageName.parse("apache/kafka:3.9.1"));

    private static DataSource dataSource;

    @BeforeAll
    static void setUpDatabaseAndTopic() throws Exception {
        PGSimpleDataSource source = new PGSimpleDataSource();
        source.setUrl(POSTGRES.getJdbcUrl());
        source.setUser(POSTGRES.getUsername());
        source.setPassword(POSTGRES.getPassword());
        Flyway.configure().dataSource(source).locations("classpath:db/migration").load().migrate();
        dataSource = source;

        try (Admin admin = Admin.create(Collections.singletonMap("bootstrap.servers", KAFKA.getBootstrapServers()))) {
            admin.createTopics(Collections.singletonList(new NewTopic(TOPIC, 1, (short) 1))).all().get();
        }
    }

    @Test
    void shouldPersistProjectPublishAndDeduplicatePayment() {
        JdbcOrderTransactionPort transaction = new JdbcOrderTransactionPort(dataSource);
        JdbcOrderRepository repository = new JdbcOrderRepository(transaction);
        ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        JdbcOutboxPort outbox = new JdbcOutboxPort(transaction, objectMapper);
        JdbcOrderReadModelPort readModels = new JdbcOrderReadModelPort(transaction);

        try (JedisPooled jedis = new JedisPooled(REDIS.getHost(), REDIS.getFirstMappedPort());
             Producer<String, String> producer = new KafkaProducer<>(producerProperties());
             Consumer<String, String> consumer = new KafkaConsumer<>(consumerProperties())) {
            OrderApplicationService applicationService = new OrderApplicationService(repository, outbox, readModels,
                    new RedisIdempotencyPort(jedis), transaction);
            TransactionalOutboxPublisher publisher = new TransactionalOutboxPublisher(transaction,
                    new OutboxPublisher(outbox, new KafkaIntegrationEventPublisher(producer, objectMapper, TOPIC)));
            consumer.subscribe(Collections.singletonList(TOPIC));

            Order created = applicationService.create(new CreateOrderCommand("ORDER-IT-001", "buyer-1", "Alice"));
            applicationService.addLine(new AddOrderLineCommand(created.id(), "goods-1", "DDD Book", 2,
                    new BigDecimal("59.90")));
            applicationService.pay(created.id(), "payment-ORDER-IT-001");
            applicationService.pay(created.id(), "payment-ORDER-IT-001");

            OrderReadModel projection = applicationService.find(created.id());
            assertThat(projection.status()).isEqualTo(OrderStatus.PAID);
            assertThat(projection.totalAmount()).isEqualByComparingTo("119.80");
            assertThat(applicationService.query(new OrderQuery("buyer-1", OrderStatus.PAID, 1, 10))).hasSize(1);

            OutboxDispatchResult dispatch = publisher.publishPending(100);
            assertThat(dispatch.failed()).isZero();
            assertThat(dispatch.published()).isGreaterThanOrEqualTo(3);
            assertThat(readOutboxStatus()).containsOnly("PUBLISHED");

            ConsumerRecords<String, String> records = consumer.poll(Duration.ofSeconds(20));
            assertThat(records.count()).isGreaterThanOrEqualTo(3);
            assertThat(records.records(TOPIC))
                    .anySatisfy(record -> assertThat(record.value()).contains("OrderPaidEvent"));
        }
    }

    @Test
    void shouldClaimConfirmRescheduleAndReplayReliableOutboxRecords() {
        JdbcOrderTransactionPort transaction = new JdbcOrderTransactionPort(dataSource);
        JdbcMQOutboxStore store = new JdbcMQOutboxStore(transaction);
        MQDeliveryPolicy policy = MQDeliveryPolicy.productionDefault();
        Instant now = Instant.now();
        String publishedId = "lease-published-" + UUID.randomUUID();
        String failedId = "lease-failed-" + UUID.randomUUID();

        transaction.execute(() -> {
            store.append(MQOutboxRecord.pending(publishedId, "orders.created", "{}", Collections.emptyMap(), now));
            store.append(MQOutboxRecord.pending(failedId, "orders.created", "{}", Collections.emptyMap(), now));
        });

        List<MQOutboxRecord> claimed = store.claim("test-instance", now.plusSeconds(1), 10, policy);
        assertThat(claimed).extracting(MQOutboxRecord::messageId).contains(publishedId, failedId);
        assertThat(store.markPublished(publishedId, "test-instance", now.plusSeconds(2))).isTrue();
        assertThat(store.reschedule(failedId, "test-instance", now.plusSeconds(2), "broker unavailable", policy)).isTrue();

        assertThat(store.replay(failedId, now.plusSeconds(3))).isFalse();

        for (int attempt = 2; attempt <= policy.maxAttempts(); attempt++) {
            Instant attemptTime = now.plusSeconds(attempt * 1_000L);
            List<MQOutboxRecord> retry = store.claim("test-instance", attemptTime, 10, policy);
            MQOutboxRecord record = retry.stream()
                    .filter(candidate -> candidate.messageId().equals(failedId))
                    .findFirst()
                    .orElseThrow(() -> new NoSuchElementException("retry record not found: " + failedId));
            assertThat(record.attempts()).isEqualTo(attempt);
            assertThat(store.reschedule(failedId, "test-instance", attemptTime,
                    "broker unavailable", policy)).isTrue();
        }

        assertThat(store.replay(failedId, now.plusSeconds(2000))).isTrue();
    }

    private Map<String, Object> producerProperties() {
        Map<String, Object> properties = new HashMap<>();
        properties.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers());
        properties.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        properties.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        properties.put(ProducerConfig.ACKS_CONFIG, "all");
        return properties;
    }

    private Map<String, Object> consumerProperties() {
        Map<String, Object> properties = new HashMap<>();
        properties.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers());
        properties.put(ConsumerConfig.GROUP_ID_CONFIG, "ddd4j-order-it-" + UUID.randomUUID());
        properties.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        properties.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        properties.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        return properties;
    }

    private List<String> readOutboxStatus() {
        JdbcOrderTransactionPort transaction = new JdbcOrderTransactionPort(dataSource);
        return transaction.query(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT status FROM sample_order_outbox ORDER BY occurred_at");
                 ResultSet rows = statement.executeQuery()) {
                ArrayList<String> statuses = new ArrayList<>();
                while (rows.next()) {
                    statuses.add(rows.getString(1));
                }
                return statuses;
            }
        });
    }
}
