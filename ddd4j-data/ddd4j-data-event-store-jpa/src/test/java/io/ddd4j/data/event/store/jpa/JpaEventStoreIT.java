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
package io.ddd4j.data.event.store.jpa;

import io.ddd4j.core.cqrs.eventstore.EventStore;
import io.ddd4j.core.cqrs.eventstore.StoredEvent;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.EntityTransaction;
import org.hibernate.cfg.Configuration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link JpaEventStore} 集成测试——H2 内存数据库全量契约验证。
 *
 * <p>覆盖场景：
 * <ol>
 *   <li>append 后 read 单流按版本顺序返回</li>
 *   <li>乐观锁：expectedVersion 不匹配抛 {@link IllegalStateException} 且不落库</li>
 *   <li>readAll 按 position 升序 + limit 分页</li>
 *   <li>事件 payload 往返（写入→读出 event 内容一致，类型还原）</li>
 *   <li>多聚合 append 交错时全局 position 单调</li>
 * </ol>
 *
 * <p>纯 JPA + H2 内存数据库，不依赖 Spring。使用 Hibernate 原生 API 配置 EntityManagerFactory。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 3.0.0
 */
class JpaEventStoreIT {

    private static EntityManagerFactory emf;
    private EntityManager em;
    private EventStore eventStore;

    @BeforeAll
    static void createEntityManagerFactory() {
        Configuration configuration = new Configuration();
        configuration.setProperty("hibernate.connection.driver_class", "org.h2.Driver");
        configuration.setProperty("hibernate.connection.url", "jdbc:h2:mem:eventstore_test;DB_CLOSE_DELAY=-1");
        configuration.setProperty("hibernate.connection.username", "sa");
        configuration.setProperty("hibernate.connection.password", "");
        configuration.setProperty("hibernate.dialect", "org.hibernate.dialect.H2Dialect");
        configuration.setProperty("hibernate.hbm2ddl.auto", "update");
        configuration.setProperty("hibernate.show_sql", "false");
        configuration.setProperty("hibernate.format_sql", "true");
        configuration.addAnnotatedClass(StoredEventEntity.class);
        emf = configuration.buildSessionFactory();
    }

    @AfterAll
    static void closeEntityManagerFactory() {
        if (emf != null && emf.isOpen()) {
            emf.close();
        }
    }

    @BeforeEach
    void setUp() {
        em = emf.createEntityManager();
        eventStore = new JpaEventStore(em);
    }

    @AfterEach
    void tearDown() {
        // 清理测试数据
        EntityTransaction tx = em.getTransaction();
        tx.begin();
        em.createQuery("DELETE FROM StoredEventEntity").executeUpdate();
        tx.commit();
        em.close();
    }

    @Test
    void appendThenReadShouldReturnEventsInVersionOrder() {
        // given
        String aggregateId = "order-001";
        OrderCreatedEvent event1 = new OrderCreatedEvent("order-001", "customer-001");
        OrderItemAddedEvent event2 = new OrderItemAddedEvent("order-001", "item-001", 2);
        OrderItemAddedEvent event3 = new OrderItemAddedEvent("order-001", "item-002", 1);

        // when
        eventStore.append(aggregateId, List.of(event1, event2, event3), 0);

        // then
        List<StoredEvent> events = eventStore.read(aggregateId);
        assertThat(events).hasSize(3);
        assertThat(events.get(0).version()).isEqualTo(0);
        assertThat(events.get(1).version()).isEqualTo(1);
        assertThat(events.get(2).version()).isEqualTo(2);
        assertThat(events.get(0).aggregateId()).isEqualTo("order-001");
    }

    @Test
    void appendWithWrongExpectedVersionShouldThrowAndNotPersist() {
        // given
        String aggregateId = "order-002";
        OrderCreatedEvent event1 = new OrderCreatedEvent("order-002", "customer-002");
        eventStore.append(aggregateId, List.of(event1), 0);

        OrderItemAddedEvent event2 = new OrderItemAddedEvent("order-002", "item-003", 5);

        // when/then
        assertThatThrownBy(() -> eventStore.append(aggregateId, List.of(event2), 0))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Version conflict");

        // 验证未落库
        List<StoredEvent> events = eventStore.read(aggregateId);
        assertThat(events).hasSize(1);
        assertThat(events.get(0).version()).isEqualTo(0);
    }

    @Test
    void readAllShouldReturnEventsInPositionOrderWithLimit() {
        // given
        String aggregateId1 = "order-010";
        String aggregateId2 = "order-020";
        OrderCreatedEvent event1 = new OrderCreatedEvent("order-010", "customer-010");
        OrderCreatedEvent event2 = new OrderCreatedEvent("order-020", "customer-020");
        OrderItemAddedEvent event3 = new OrderItemAddedEvent("order-010", "item-010", 1);

        eventStore.append(aggregateId1, List.of(event1), 0);
        eventStore.append(aggregateId2, List.of(event2), 0);
        eventStore.append(aggregateId1, List.of(event3), 1);

        // when
        List<StoredEvent> allEvents = eventStore.readAll(0, 10);

        // then
        assertThat(allEvents).hasSize(3);
        // position 应该单调递增
        assertThat(allEvents.get(0).position()).isLessThan(allEvents.get(1).position());
        assertThat(allEvents.get(1).position()).isLessThan(allEvents.get(2).position());

        // 测试 limit
        List<StoredEvent> limitedEvents = eventStore.readAll(0, 2);
        assertThat(limitedEvents).hasSize(2);
    }

    @Test
    void eventPayloadShouldSurviveRoundTrip() {
        // given
        String aggregateId = "order-030";
        OrderCreatedEvent originalEvent = new OrderCreatedEvent("order-030", "customer-030");

        // when
        eventStore.append(aggregateId, List.of(originalEvent), 0);

        // then
        List<StoredEvent> events = eventStore.read(aggregateId);
        assertThat(events).hasSize(1);

        Object restoredEvent = events.get(0).event();
        assertThat(restoredEvent).isInstanceOf(OrderCreatedEvent.class);

        OrderCreatedEvent typedEvent = (OrderCreatedEvent) restoredEvent;
        assertThat(typedEvent.orderId()).isEqualTo("order-030");
        assertThat(typedEvent.customerId()).isEqualTo("customer-030");
    }

    @Test
    void multipleAggregatesShouldHaveMonotonicallyIncreasingPositions() {
        // given
        String aggregateId1 = "order-040";
        String aggregateId2 = "order-050";
        String aggregateId3 = "order-060";

        OrderCreatedEvent event1 = new OrderCreatedEvent("order-040", "customer-040");
        OrderCreatedEvent event2 = new OrderCreatedEvent("order-050", "customer-050");
        OrderCreatedEvent event3 = new OrderCreatedEvent("order-060", "customer-060");

        // when - 交错 append
        eventStore.append(aggregateId1, List.of(event1), 0);
        eventStore.append(aggregateId2, List.of(event2), 0);
        eventStore.append(aggregateId3, List.of(event3), 0);

        // then - 全局 position 严格单调
        List<StoredEvent> allEvents = eventStore.readAll(0, 100);
        assertThat(allEvents).hasSize(3);

        long previousPosition = -1;
        for (StoredEvent event : allEvents) {
            assertThat(event.position()).isGreaterThan(previousPosition);
            previousPosition = event.position();
        }
    }

    // =================== 测试事件类 ===================

    record OrderCreatedEvent(String orderId, String customerId) {
    }

    record OrderItemAddedEvent(String orderId, String itemId, int quantity) {
    }
}
