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
import io.ddd4j.sample.order.application.OutboxPublisher;
import io.ddd4j.sample.order.application.OutboxDispatchResult;
import io.ddd4j.sample.order.application.OrderReadModel;
import io.ddd4j.sample.order.application.OrderApplicationService;
import io.ddd4j.sample.order.application.CreateOrderCommand;
import io.ddd4j.sample.order.application.AddOrderLineCommand;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.redis.testcontainers.RedisContainer;
import io.ddd4j.mq.delivery.MQDeliveryHeaders;
import io.ddd4j.mq.delivery.MQDeliveryPolicy;
import io.ddd4j.mq.delivery.MQOutboxDispatchResult;
import io.ddd4j.mq.delivery.MQOutboxDispatcher;
import io.ddd4j.mq.delivery.MQOutboxRecord;
import io.ddd4j.mq.delivery.MQOutboxStore;
import io.ddd4j.sample.order.application.*;
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
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 共享订单业务内核的真实基础设施闭环测试。
 *
 * <p>使用 PostgreSQL、Redis 与 Kafka 容器验证同一订单事务内的写模型、读模型和 Outbox，及其后续的
 * broker ACK 发布与支付幂等。无 Docker 时由 Testcontainers 自动跳过。
 */
@Testcontainers(disabledWithoutDocker = true)
class OrderInfrastructureRoundTripTest {

    private static final String TOPIC = "ddd4j-sample-order-it";

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("postgres:16-alpine"))
            .withTmpFs(Collections.singletonMap("/var/lib/postgresql/data", "rw,size=512m"));

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
    void shouldPersistProjectPublishAndDeduplicatePayment() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

        try (JedisPooled jedis = new JedisPooled(REDIS.getHost(), REDIS.getFirstMappedPort());
             Producer<String, String> producer = new KafkaProducer<>(producerProperties());
             Consumer<String, String> consumer = new KafkaConsumer<>(consumerProperties())) {
            try (JdbcOrderSampleRuntime runtime = JdbcOrderSampleRuntime.start(dataSource, objectMapper,
                    new RedisIdempotencyPort(jedis), new KafkaIntegrationEventPublisher(producer, objectMapper, TOPIC),
                    "order-sample-worker", 100, Duration.ofMillis(50), java.time.Clock.systemUTC())) {
                OrderApplicationService applicationService = runtime.applicationService();
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

                List<String> statuses;
                long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
                do {
                    statuses = readOutboxStatus(created.id());
                    if (statuses.size() >= 3 && statuses.stream().allMatch("PUBLISHED"::equals)) {
                        break;
                    }
                    TimeUnit.MILLISECONDS.sleep(50);
                } while (System.nanoTime() < deadline);
                assertThat(statuses).hasSizeGreaterThanOrEqualTo(3).containsOnly("PUBLISHED");

                ConsumerRecords<String, String> records = consumer.poll(Duration.ofSeconds(20));
                assertThat(records.count()).isGreaterThanOrEqualTo(3);
                assertThat(records.records(TOPIC))
                        .anySatisfy(record -> assertThat(record.value()).contains("OrderPaidEvent"));
            }
        }
    }

    @Test
    void shouldKeepCommittedPaymentIdempotentWhenRedisCompletionFails() {
        JdbcOrderTransactionPort transaction = new JdbcOrderTransactionPort(dataSource);
        JdbcOrderRepository repository = new JdbcOrderRepository(transaction);
        JdbcOutboxPort outbox = new JdbcOutboxPort(transaction,
                new ObjectMapper().registerModule(new JavaTimeModule()));
        JdbcOrderReadModelPort readModels = new JdbcOrderReadModelPort(transaction);
        AtomicInteger releases = new AtomicInteger();

        try (JedisPooled jedis = new JedisPooled(REDIS.getHost(), REDIS.getFirstMappedPort())) {
            RedisIdempotencyPort redis = new RedisIdempotencyPort(jedis);
            IdempotencyPort failingCompletion = new IdempotencyPort() {
                @Override
                public boolean acquire(String key, Duration ttl) {
                    return redis.acquire(key, ttl);
                }

                @Override
                public void complete(String key, Object result) {
                    throw new IllegalStateException("Redis completion unavailable");
                }

                @Override
                public void release(String key) {
                    releases.incrementAndGet();
                    redis.release(key);
                }
            };
            OrderApplicationService application = new OrderApplicationService(
                    repository, outbox, readModels, failingCompletion, transaction);
            Order order = application.create(new CreateOrderCommand(
                    "ORDER-COMPLETE-" + UUID.randomUUID(), "buyer-complete", "Alice"));
            application.addLine(new AddOrderLineCommand(order.id(), "goods-1", "DDD Book", 1,
                    new BigDecimal("59.90")));
            String key = "payment-complete-" + UUID.randomUUID();

            assertThatThrownBy(() -> application.pay(order.id(), key))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("Redis completion unavailable");
            assertThat(repository.findById(order.id())).hasValueSatisfying(
                    persisted -> assertThat(persisted.status()).isEqualTo(OrderStatus.PAID));
            List<String> outboxBeforeRetry = readOutboxStatus(order.id());
            assertThat(outboxBeforeRetry).hasSize(3);
            assertThat(releases.get()).isZero();

            Order retry = application.pay(order.id(), key);

            assertThat(retry.status()).isEqualTo(OrderStatus.PAID);
            assertThat(readOutboxStatus(order.id())).isEqualTo(outboxBeforeRetry);
        }
    }

    @Test
    void shouldPreserveCausalHeadersAcrossJdbcOutboxClaim() {
        JdbcOrderTransactionPort transaction = new JdbcOrderTransactionPort(dataSource);
        JdbcMQOutboxStore store = new JdbcMQOutboxStore(transaction);
        Instant now = Instant.now();
        String messageId = "causal-headers-" + UUID.randomUUID();
        Map<String, String> headers = new java.util.LinkedHashMap<>();
        headers.put(MQDeliveryHeaders.CORRELATION_ID, "correlation-123");
        headers.put(MQDeliveryHeaders.CAUSATION_ID, "causation-456");
        transaction.execute(() -> store.append(MQOutboxRecord.pending(
                messageId, TOPIC, "{}", headers, now)));

        MQOutboxRecord claimed = store.claim("header-owner", now.plusSeconds(1), 1,
                MQDeliveryPolicy.productionDefault()).stream()
                .filter(record -> record.messageId().equals(messageId))
                .findFirst().orElseThrow(() -> new AssertionError("Outbox message was not claimed"));

        assertThat(claimed.headers()).containsEntry(MQDeliveryHeaders.CORRELATION_ID, "correlation-123")
                .containsEntry(MQDeliveryHeaders.CAUSATION_ID, "causation-456");
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
            store.append(MQOutboxRecord.pending(publishedId, "orders.created", "{}", Collections.<String, String>emptyMap(), now));
            store.append(MQOutboxRecord.pending(failedId, "orders.created", "{}", Collections.<String, String>emptyMap(), now));
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

    @Test
    void concurrentOwnersMustNotClaimTheSameActiveLease() throws Exception {
        JdbcOrderTransactionPort transaction = new JdbcOrderTransactionPort(dataSource);
        JdbcMQOutboxStore store = new JdbcMQOutboxStore(transaction);
        MQDeliveryPolicy policy = MQDeliveryPolicy.productionDefault();
        Instant now = Instant.parse("2000-01-01T00:00:00Z");
        String messageId = "lease-concurrent-" + UUID.randomUUID();
        transaction.execute(() -> store.append(MQOutboxRecord.pending(
                messageId, TOPIC, "{}", Collections.<String, String>emptyMap(), now)));

        ExecutorService workers = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<List<MQOutboxRecord>> first = workers.submit(() -> {
                ready.countDown();
                start.await();
                return new JdbcMQOutboxStore(new JdbcOrderTransactionPort(dataSource))
                        .claim("owner-a", now.plusSeconds(1), 1, policy);
            });
            Future<List<MQOutboxRecord>> second = workers.submit(() -> {
                ready.countDown();
                start.await();
                return new JdbcMQOutboxStore(new JdbcOrderTransactionPort(dataSource))
                        .claim("owner-b", now.plusSeconds(1), 1, policy);
            });
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            List<MQOutboxRecord> firstClaim = first.get(10, TimeUnit.SECONDS);
            List<MQOutboxRecord> secondClaim = second.get(10, TimeUnit.SECONDS);
            assertThat(firstClaim.size() + secondClaim.size()).isEqualTo(1);
            MQOutboxRecord claimed = firstClaim.isEmpty() ? secondClaim.get(0) : firstClaim.get(0);
            assertThat(claimed.messageId()).isEqualTo(messageId);
            assertThat(claimed.attempts()).isEqualTo(1);

            String loser = "owner-a".equals(claimed.leaseOwner()) ? "owner-b" : "owner-a";
            assertThat(store.markPublished(messageId, loser, now.plusSeconds(2))).isFalse();
            assertThat(store.markPublished(messageId, claimed.leaseOwner(), now.plusSeconds(2))).isTrue();
        } finally {
            start.countDown();
            workers.shutdownNow();
            assertThat(workers.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void shouldTransferExpiredLeaseAndRejectStaleOwner() {
        JdbcOrderTransactionPort transaction = new JdbcOrderTransactionPort(dataSource);
        JdbcMQOutboxStore store = new JdbcMQOutboxStore(transaction);
        MQDeliveryPolicy policy = MQDeliveryPolicy.productionDefault();
        Instant now = Instant.now();
        String messageId = "lease-takeover-" + UUID.randomUUID();
        transaction.execute(() -> store.append(MQOutboxRecord.pending(
                messageId, TOPIC, "{}", Collections.<String, String>emptyMap(), now)));

        assertThat(store.claim("owner-a", now.plusSeconds(1), 1, policy))
                .extracting(MQOutboxRecord::messageId).containsExactly(messageId);
        assertThat(store.claim("owner-b", now.plusSeconds(2), 1, policy)).isEmpty();

        Instant afterExpiry = now.plusSeconds(2).plus(policy.leaseDuration());
        List<MQOutboxRecord> transferred = store.claim("owner-b", afterExpiry, 1, policy);
        assertThat(transferred).extracting(MQOutboxRecord::messageId).containsExactly(messageId);
        assertThat(transferred.get(0).attempts()).isEqualTo(2);
        assertThat(store.markPublished(messageId, "owner-a", afterExpiry)).isFalse();
        assertThat(store.markPublished(messageId, "owner-b", afterExpiry)).isTrue();
        assertThat(store.claim("owner-c", afterExpiry.plusSeconds(1), 1, policy))
                .extracting(MQOutboxRecord::messageId).doesNotContain(messageId);
    }

    @Test
    void shouldRedeliverToKafkaAfterConfirmationIsLost() {
        verifyRedeliveryAfterConfirmationIsLost(false);
    }

    @Test
    void shouldRedeliverToKafkaAfterConfirmationThrows() {
        verifyRedeliveryAfterConfirmationIsLost(true);
    }

    private void verifyRedeliveryAfterConfirmationIsLost(boolean throwOnFirstConfirmation) {
        JdbcOrderTransactionPort transaction = new JdbcOrderTransactionPort(dataSource);
        JdbcMQOutboxStore store = new JdbcMQOutboxStore(transaction);
        MQDeliveryPolicy policy = MQDeliveryPolicy.productionDefault();
        Instant now = Instant.parse("1970-01-01T00:00:00Z");
        String messageId = "lost-confirmation-" + UUID.randomUUID();
        transaction.execute(() -> store.append(MQOutboxRecord.pending(
                messageId, TOPIC, "{}", Collections.<String, String>emptyMap(), now)));
        AtomicBoolean loseFirstConfirmation = new AtomicBoolean(true);
        MQOutboxStore unreliableConfirmation = new MQOutboxStore() {
            @Override
            public void append(MQOutboxRecord record) {
                store.append(record);
            }

            @Override
            public List<MQOutboxRecord> claim(String owner, Instant at, int limit, MQDeliveryPolicy deliveryPolicy) {
                return store.claim(owner, at, limit, deliveryPolicy);
            }

            @Override
            public boolean markPublished(String id, String owner, Instant at) {
                if (loseFirstConfirmation.getAndSet(false)) {
                    if (throwOnFirstConfirmation) {
                        throw new IllegalStateException("confirmation outcome unknown");
                    }
                    return false;
                }
                return store.markPublished(id, owner, at);
            }

            @Override
            public boolean reschedule(String id, String owner, Instant at, String error,
                                      MQDeliveryPolicy deliveryPolicy) {
                return store.reschedule(id, owner, at, error, deliveryPolicy);
            }

            @Override
            public boolean replay(String id, Instant at) {
                return store.replay(id, at);
            }
        };

        try (Producer<String, String> producer = new KafkaProducer<>(producerProperties());
             Consumer<String, String> consumer = new KafkaConsumer<>(consumerProperties())) {
            consumer.subscribe(Collections.singletonList(TOPIC));
            MQOutboxDispatcher dispatcher = new MQOutboxDispatcher(unreliableConfirmation, record -> {
                assertThat(record.messageId()).isEqualTo(messageId);
                try {
                    producer.send(new ProducerRecord<>(TOPIC, record.messageId(), record.payload()))
                            .get(10, TimeUnit.SECONDS);
                } catch (Exception exception) {
                    throw new IllegalStateException("Kafka send failed", exception);
                }
            }, policy);

            MQOutboxDispatchResult first = dispatcher.dispatch("owner-a", 1, now.plusSeconds(1));
            assertThat(first.confirmationLost()).isEqualTo(1);
            assertThat(first.published()).isZero();
            assertThat(first.rescheduled()).isZero();

            Instant afterExpiry = now.plusSeconds(2).plus(policy.leaseDuration());
            MQOutboxDispatchResult second = dispatcher.dispatch("owner-b", 1, afterExpiry);
            assertThat(second.published()).isEqualTo(1);
            assertThat(store.markPublished(messageId, "owner-a", afterExpiry)).isFalse();

            int deliveries = 0;
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
            while (deliveries < 2 && System.nanoTime() < deadline) {
                for (ConsumerRecord<String, String> record : consumer.poll(Duration.ofSeconds(1)).records(TOPIC)) {
                    if (messageId.equals(record.key())) {
                        deliveries++;
                    }
                }
            }
            assertThat(deliveries).isEqualTo(2);
        }
    }

    @Test
    void shouldRetryFailedSendAndPublishToKafka() {
        JdbcOrderTransactionPort transaction = new JdbcOrderTransactionPort(dataSource);
        JdbcMQOutboxStore store = new JdbcMQOutboxStore(transaction);
        MQDeliveryPolicy policy = MQDeliveryPolicy.productionDefault();
        Instant now = Instant.now();
        String messageId = "send-retry-" + UUID.randomUUID();
        transaction.execute(() -> store.append(MQOutboxRecord.pending(
                messageId, TOPIC, "{}", Collections.<String, String>emptyMap(), now)));
        AtomicBoolean failFirstSend = new AtomicBoolean(true);

        try (Producer<String, String> producer = new KafkaProducer<>(producerProperties());
             Consumer<String, String> consumer = new KafkaConsumer<>(consumerProperties())) {
            consumer.subscribe(Collections.singletonList(TOPIC));
            MQOutboxDispatcher dispatcher = new MQOutboxDispatcher(store, record -> {
                if (failFirstSend.getAndSet(false)) {
                    throw new IllegalStateException("injected broker outage");
                }
                try {
                    producer.send(new ProducerRecord<>(TOPIC, record.messageId(), record.payload()))
                            .get(10, TimeUnit.SECONDS);
                } catch (Exception exception) {
                    throw new IllegalStateException("Kafka send failed", exception);
                }
            }, policy);

            MQOutboxDispatchResult failed = dispatcher.dispatch("owner-a", 1, now.plusSeconds(1));
            assertThat(failed.rescheduled()).isEqualTo(1);
            assertThat(failed.published()).isZero();

            MQOutboxDispatchResult retried = dispatcher.dispatch("owner-b", 1, now.plusSeconds(10));
            assertThat(retried.published()).isEqualTo(1);
            assertThat(retried.rescheduled()).isZero();

            int deliveries = 0;
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
            while (deliveries < 1 && System.nanoTime() < deadline) {
                for (ConsumerRecord<String, String> record : consumer.poll(Duration.ofSeconds(1)).records(TOPIC)) {
                    if (messageId.equals(record.key())) {
                        deliveries++;
                    }
                }
            }
            assertThat(deliveries).isEqualTo(1);
        }
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

    private List<String> readOutboxStatus(String aggregateId) {
        JdbcOrderTransactionPort transaction = new JdbcOrderTransactionPort(dataSource);
        return transaction.query(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT status FROM sample_order_outbox WHERE aggregate_id = ? ORDER BY occurred_at")) {
                statement.setString(1, aggregateId);
                try (ResultSet rows = statement.executeQuery()) {
                    ArrayList<String> statuses = new ArrayList<>();
                    while (rows.next()) {
                        statuses.add(rows.getString(1));
                    }
                    return statuses;
                }
            }
        });
    }
}
