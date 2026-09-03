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
package io.ddd4j.data.event.store.panache;

import io.ddd4j.core.cqrs.eventstore.AggregateVersionConflictException;
import io.ddd4j.core.cqrs.eventstore.EventStore;
import io.ddd4j.core.ddd.event.AggregateRootId;
import io.ddd4j.core.ddd.event.DomainEvent;
import io.ddd4j.core.ddd.event.EntityIdPath;
import io.ddd4j.core.ddd.event.EntityType;
import io.ddd4j.core.ddd.event.StringEntityType;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.PersistenceException;
import org.hibernate.cfg.Configuration;
import org.hibernate.resource.jdbc.spi.StatementInspector;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link PanacheEventStore} append 重试集成测试。
 *
 * <p>每个测试独立构建 EntityManagerFactory（与 FaultInjector 隔离），避免
 * Hibernate {@link StatementInspector} 单例计数在测试间共享。
 *
 * <p>覆盖四类端到端场景：
 * <ol>
 *   <li><b>成功路径</b>：append 不触发 Sleeper，事件正确落库</li>
 *   <li><b>乐观锁失败</b>：expectedVersion 不匹配抛 IllegalStateException，<b>不</b>触发 Sleeper，事务回滚</li>
 *   <li><b>真实 uk_position 冲突 + 重试恢复</b>：使用 Hibernate
 *       {@link StatementInspector} 在第一次 INSERT 抛 {@link PersistenceException}
 *       （含 "uk_position" 关键字），第二次让 SQL 正常通过——验证 EventStoreRetry 自动捕获并重试成功</li>
 *   <li><b>空事件列表</b>：早期返回，不触发 Sleeper</li>
 * </ol>
 */
class PanacheEventStoreRetryTest {

    private static final String ORDER_TYPE = "Order";

    static final class CountingSleeper implements EventStoreRetry.Sleeper {
        final AtomicInteger calls = new AtomicInteger();
        final List<Long> delays = new ArrayList<>();

        @Override
        public void sleep(long millis) {
            calls.incrementAndGet();
            delays.add(millis);
        }
    }

    /**
     * Hibernate 6 的 SQL 拦截器：第一次 INSERT 调用抛 PersistenceException（模拟
     * uk_position 冲突），第二次正常透传。等价于生产中"两个事务并发争抢 uk_position
     * → 第一个回滚 → 第二个重试成功"的场景。
     */
    public static final class FaultInjector implements StatementInspector {
        public FaultInjector() {
        }

        final AtomicInteger insertCount = new AtomicInteger();

        @Override
        public String inspect(String sql) {
            if (sql != null && sql.trim().toUpperCase().startsWith("INSERT")
                    && insertCount.incrementAndGet() == 1) {
                throw new PersistenceException(
                        "could not execute statement: Unique index uk_position violation");
            }
            return sql;
        }
    }

    private EntityManagerFactory emf;
    private EntityManager em;
    private EventStore eventStore;
    private CountingSleeper sleeper;

    private void buildEmf(boolean withFaultInjector) {
        Configuration configuration = new Configuration()
                .setProperty("hibernate.dialect", "org.hibernate.dialect.H2Dialect")
                .setProperty("hibernate.connection.driver_class", "org.h2.Driver")
                .setProperty("hibernate.connection.url",
                        "jdbc:h2:mem:panache_retry_" + System.nanoTime() + ";DB_CLOSE_DELAY=-1")
                .setProperty("hibernate.connection.username", "sa")
                .setProperty("hibernate.connection.password", "")
                .setProperty("hibernate.hbm2ddl.auto", "update")
                .setProperty("hibernate.show_sql", "false")
                .addAnnotatedClass(PanacheStoredEventEntity.class);
        if (withFaultInjector) {
            configuration.setProperty("hibernate.session_factory.statement_inspector",
                    FaultInjector.class.getName());
        }
        emf = configuration.buildSessionFactory();
    }

    private void setupStore() {
        sleeper = new CountingSleeper();
        EventStoreRetry retry = new EventStoreRetry(5, 1L, sleeper);
        eventStore = new PanacheEventStore(em, retry);
    }

    @BeforeEach
    void setUpBase() {
        buildEmf(false);
        em = emf.createEntityManager();
        setupStore();
    }

    @AfterEach
    void tearDown() {
        if (em != null && em.isOpen()) {
            em.close();
        }
        if (emf != null && emf.isOpen()) {
            emf.close();
        }
    }

    @Test
    void append_成功路径_不触发Sleeper() {
        TestAggregateRootId orderId = new TestAggregateRootId("agg-1");
        eventStore.append(ORDER_TYPE, orderId, List.of(new TestEvent(orderId)), 0L);

        assertThat(sleeper.calls.get()).isZero();
        assertThat(eventStore.read(ORDER_TYPE, orderId)).hasSize(1);
    }

    @Test
    void append_期望版本不匹配_强类型冲突异常_不触发重试_事务回滚() {
        TestAggregateRootId orderId = new TestAggregateRootId("agg-1");
        eventStore.append(ORDER_TYPE, orderId, List.of(new TestEvent(orderId)), 0L);

        assertThatThrownBy(() ->
                eventStore.append(ORDER_TYPE, orderId, List.of(new TestEvent(orderId)), 99L))
                .isInstanceOf(AggregateVersionConflictException.class);

        assertThat(sleeper.calls.get())
                .as("聚合版本冲突是乐观锁失败信号，不应触发重试")
                .isZero();
        assertThat(eventStore.read(ORDER_TYPE, orderId)).hasSize(1);
    }

    @Test
    void append_uk_position冲突_触发自动重试_最终成功() {
        // 重建带 FaultInjector 的独立 EMF（不与成功路径/乐观锁路径的 EMF 共享）
        em.close();
        emf.close();
        buildEmf(true);
        em = emf.createEntityManager();
        setupStore();

        // 应成功：第一次 INSERT 触发 uk_position 冲突，重试后第二次 INSERT 成功
        // （FaultInjector 仅第一次抛错，第二次透传真实 SQL；Hibernate 事务 rollback
        // 后 EntityManager 状态清空，重试走全新事务）
        TestAggregateRootId orderId = new TestAggregateRootId("agg-conflict");
        eventStore.append(ORDER_TYPE, orderId, List.of(new TestEvent(orderId)), 0L);

        // 关键断言 1：Sleeper 至少被调用 1 次（即重试机制确实触发过）
        assertThat(sleeper.calls.get())
                .as("EventStoreRetry 应至少触发一次退避以处理 uk_position 冲突")
                .isGreaterThanOrEqualTo(1);
        // 关键断言 2：最终落库成功（重试恢复）
        assertThat(eventStore.read(ORDER_TYPE, orderId)).hasSize(1);
    }

    @Test
    void append_空事件列表_不触发Sleeper_不落库() {
        TestAggregateRootId orderId = new TestAggregateRootId("agg-empty");
        eventStore.append(ORDER_TYPE, orderId, List.of(), 0L);

        assertThat(sleeper.calls.get()).isZero();
        assertThat(eventStore.read(ORDER_TYPE, orderId)).isEmpty();
    }

    record TestAggregateRootId(String value) implements AggregateRootId {
        private static final EntityType TYPE = new StringEntityType("Order");

        @Override
        public EntityType getType() {
            return TYPE;
        }

        @Override
        public String asString() {
            return value;
        }

        @Override
        public String asTypedString() {
            return TYPE.asString() + ":" + value;
        }
    }

    static final class TestEvent extends DomainEvent<TestAggregateRootId> {
        TestEvent() {
            super();
        }

        TestEvent(TestAggregateRootId orderId) {
            super(new EntityIdPath(orderId));
        }
    }
}
