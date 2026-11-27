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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.ddd4j.core.cqrs.eventstore.AggregateVersionConflictException;
import io.ddd4j.core.cqrs.eventstore.EventStore;
import io.ddd4j.core.ddd.event.*;
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
import java.util.Objects;
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

    /** \u6d4b\u8bd5\u4e8b\u4ef6\u6d41\u4e2d\u4f7f\u7528\u7684\u805a\u5408\u7c7b\u578b\u540d\u79f0\u3002 */
    private static final String ORDER_TYPE = "Order";
    /** \u521b\u5efa JPA \u5b9e\u4f53\u7ba1\u7406\u5668\u7684\u5de5\u5382\u3002 */
    private EntityManagerFactory emf;
    /** \u6267\u884c\u6570\u636e\u8bbf\u95ee\u64cd\u4f5c\u7684 JPA \u5b9e\u4f53\u7ba1\u7406\u5668\u3002 */
    private EntityManager em;
    /** \u8bfb\u53d6\u53ca\u8ffd\u52a0\u9886\u57df\u4e8b\u4ef6\u7684\u4e8b\u4ef6\u5b58\u50a8\u3002 */
    private EventStore eventStore;
    /** \u8bb0\u5f55\u3001\u66ff\u4ee3\u6216\u63a7\u5236\u6d4b\u8bd5\u9000\u907f\u7b49\u5f85\u7684\u7ec4\u4ef6\u3002 */
    private CountingSleeper sleeper;

    /**
     * \u6839\u636e\u672c\u6b21\u8c03\u7528\u66f4\u65b0 <code>emf = configuration.buildSessionFactory()</code>\uff0c\u7528\u4e8e\u540e\u7eed\u6d4b\u8bd5\u6b65\u9aa4\u3002
     * @param withFaultInjector \u672c\u6b21\u8c03\u7528\u4f7f\u7528\u7684 <code>boolean</code> \u53c2\u6570 <code>withFaultInjector</code>
     */
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

    /** \u6839\u636e\u672c\u6b21\u8c03\u7528\u66f4\u65b0 <code>sleeper = new CountingSleeper()</code>\u3001<code>eventStore = new PanacheEventStore(em, retry)</code>\uff0c\u7528\u4e8e\u540e\u7eed\u6d4b\u8bd5\u6b65\u9aa4\u3002 */
    private void setupStore() {
        sleeper = new CountingSleeper();
        EventStoreRetry retry = new EventStoreRetry(5, 1L, sleeper);
        eventStore = new PanacheEventStore(em, retry);
    }

    /** \u6d4b\u8bd5\u6267\u884c\u524d\u6267\u884c <code>buildEmf()</code>\u3001<code>emf.createEntityManager()</code>\u3001<code>setupStore()</code> \u5b8c\u6210\u8d44\u6e90\u51c6\u5907\u3002 */
    @BeforeEach
    void setUpBase() {
        buildEmf(false);
        em = emf.createEntityManager();
        setupStore();
    }

    /** \u6d4b\u8bd5\u7ed3\u675f\u540e\u6267\u884c <code>em.isOpen()</code>\u3001<code>em.close()</code>\u3001<code>emf.isOpen()</code>\u3001<code>emf.close()</code> \u5b8c\u6210\u8d44\u6e90\u56de\u6536\u6216\u72b6\u6001\u6062\u590d\u3002 */
    @AfterEach
    void tearDown() {
        if (em != null && em.isOpen()) {
            em.close();
        }
        if (emf != null && emf.isOpen()) {
            emf.close();
        }
    }

    /** \u9a8c\u8bc1<code>sleeper.calls.get()</code>\u4e3a\u96f6\uff0c<code>eventStore.read(ORDER_TYPE, orderId)</code> \u7684\u5927\u5c0f\u4e3a <code>1</code>\u3002 */
    @Test
    void append_成功路径_不触发Sleeper() {
        TestAggregateRootId orderId = new TestAggregateRootId("agg-1");
        eventStore.append(ORDER_TYPE, orderId, java.util.Arrays.asList(new TestEvent(orderId)), 0L);

        assertThat(sleeper.calls.get()).isZero();
        assertThat(eventStore.read(ORDER_TYPE, orderId)).hasSize(1);
    }

    /** \u9a8c\u8bc1<code>eventStore.append(ORDER_TYPE, orderId, java.util.Arrays.asList(new TestEvent(orderId)), 99L)</code> \u629b\u51fa <code>AggregateVersionConflictException.class</code>\uff0c<code>eventStore.read(ORDER_TYPE, orderId)</code> \u7684\u5927\u5c0f\u4e3a <code>1</code>\u3002 */
    @Test
    void append_期望版本不匹配_强类型冲突异常_不触发重试_事务回滚() {
        TestAggregateRootId orderId = new TestAggregateRootId("agg-1");
        eventStore.append(ORDER_TYPE, orderId, java.util.Arrays.asList(new TestEvent(orderId)), 0L);

        assertThatThrownBy(() ->
                eventStore.append(ORDER_TYPE, orderId, java.util.Arrays.asList(new TestEvent(orderId)), 99L))
                .isInstanceOf(AggregateVersionConflictException.class);

        assertThat(sleeper.calls.get())
                .as("聚合版本冲突是乐观锁失败信号，不应触发重试")
                .isZero();
        assertThat(eventStore.read(ORDER_TYPE, orderId)).hasSize(1);
    }

    /** \u9a8c\u8bc1<code>eventStore.read(ORDER_TYPE, orderId)</code> \u7684\u5927\u5c0f\u4e3a <code>1</code>\u3002 */
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
        eventStore.append(ORDER_TYPE, orderId, java.util.Arrays.asList(new TestEvent(orderId)), 0L);

        // 关键断言 1：Sleeper 至少被调用 1 次（即重试机制确实触发过）
        assertThat(sleeper.calls.get())
                .as("EventStoreRetry 应至少触发一次退避以处理 uk_position 冲突")
                .isGreaterThanOrEqualTo(1);
        // 关键断言 2：最终落库成功（重试恢复）
        assertThat(eventStore.read(ORDER_TYPE, orderId)).hasSize(1);
    }

    /** \u9a8c\u8bc1<code>sleeper.calls.get()</code>\u4e3a\u96f6\uff0c<code>eventStore.read(ORDER_TYPE, orderId)</code>\u4e3a\u7a7a\u3002 */
    @Test
    void append_空事件列表_不触发Sleeper_不落库() {
        TestAggregateRootId orderId = new TestAggregateRootId("agg-empty");
        eventStore.append(ORDER_TYPE, orderId, java.util.Collections.emptyList(), 0L);

        assertThat(sleeper.calls.get()).isZero();
        assertThat(eventStore.read(ORDER_TYPE, orderId)).isEmpty();
    }

    /** \u5b9e\u73b0 <code>Sleeper</code>\uff0c\u4f9b\u6d4b\u8bd5\u8c03\u7528 <code>sleep()</code> \u5e76\u89c2\u5bdf\u7ed3\u679c\u3002 */
    static final class CountingSleeper implements EventStoreRetry.Sleeper {
        /** \u8bb0\u5f55\u88ab\u6d4b\u5165\u53e3\u5b9e\u9645\u89e6\u53d1\u7684\u8c03\u7528\u3002 */
        final AtomicInteger calls = new AtomicInteger();
        /** \u6536\u96c6\u5b9e\u9645\u8c03\u5ea6\u6216\u91cd\u8bd5\u91c7\u7528\u7684\u5ef6\u65f6\u3002 */
        final List<Long> delays = new ArrayList<>();

        /**
         * \u8c03\u7528 <code>calls.incrementAndGet()</code>\u3001<code>delays.add()</code> \u6267\u884c\u5f53\u524d\u5939\u5177\u64cd\u4f5c\u3002
         * @param millis \u7b49\u5f85\u6216\u63a8\u8fdb\u7684\u6beb\u79d2\u6570
         */
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
        /** \u5728\u6d4b\u8bd5\u7ebf\u7a0b\u4e4b\u95f4\u5171\u4eab\u5f53\u524d\u64cd\u4f5c\u6b21\u6570\u6216\u66f4\u65b0\u6570\u91cf\u7684\u8ba1\u6570\u5668\u3002 */
        final AtomicInteger insertCount = new AtomicInteger();

        /** \u521b\u5efa\u5b57\u6bb5\u4f7f\u7528\u9ed8\u8ba4\u521d\u59cb\u503c\u7684 <code>FaultInjector</code> \u6d4b\u8bd5\u5bf9\u8c61\u3002 */
        public FaultInjector() {
        }

        /**
         * \u8bfb\u53d6 <code>sql</code> \u4f9b\u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u3002
         * @param sql \u5f53\u524d\u64cd\u4f5c\u8981\u4ea4\u7ed9\u6570\u636e\u5e93\u6267\u884c\u7684 SQL
         * @return \u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u7ed3\u679c\uff0c\u53d6\u81ea <code>sql</code>
         */
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

    /** \u6d4b\u8bd5\u4e13\u7528\u805a\u5408\u6839\u6807\u8bc6\uff0c\u4f9b\u4ed3\u50a8\u3001\u4e8b\u4ef6\u5e8f\u5217\u5316\u6216\u547d\u4ee4\u8def\u7531\u6784\u9020\u72ec\u7acb\u7684\u805a\u5408\u5b9e\u4f8b\u3002 */
    final static class TestAggregateRootId implements AggregateRootId {

        /** Java \u5e8f\u5217\u5316\u4f7f\u7528\u7684\u7248\u672c\u6807\u8bc6\u3002 */
        private static final long serialVersionUID = 0L;
        /** \u6d4b\u8bd5\u805a\u5408\u6ce8\u518c\u548c\u53cd\u5e8f\u5217\u5316\u6240\u7528\u7684\u5b9e\u4f53\u7c7b\u578b\u6807\u8bc6\u3002 */
        private static final EntityType TYPE = new StringEntityType("Order");
        /** \u7ec4\u6210\u6d4b\u8bd5\u6807\u8bc6\u7684\u539f\u59cb\u503c\u3002 */
        private final String value;

        /**
         * \u8bbe\u7f6e <code>value</code>\uff0c\u7528\u4e8e\u6784\u9020 <code>TestAggregateRootId</code> \u6d4b\u8bd5\u5b9e\u4f8b\u3002
         * @param value \u7ec4\u6210\u6d4b\u8bd5\u6807\u8bc6\u7684\u539f\u59cb\u503c
         */
        @JsonCreator()
        TestAggregateRootId(@JsonProperty("value") String value) {
            this.value = value;
        }

        /** {@inheritDoc} */
        @Override
        public EntityType getType() {
            return TYPE;
        }

        /** {@inheritDoc} */
        @Override
        public String asString() {
            return value;
        }

        /** {@inheritDoc} */
        @Override
        public String asTypedString() {
            return TYPE.asString() + ":" + value;
        }

        /**
         * \u8bfb\u53d6<code>value</code> \u5b57\u6bb5\u3002
         * @return <code>value</code> \u5b57\u6bb5
         */
        @JsonProperty("value")
        public String value() {
            return value;
        }

        /** {@inheritDoc} */
        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (Objects.isNull(obj) || getClass() != obj.getClass()) {
                return false;
            }
            TestAggregateRootId other = (TestAggregateRootId) obj;
            return Objects.equals(this.value, other.value);
        }

        /** {@inheritDoc} */
        @Override
        public int hashCode() {
            int result = 0;
            result = 31 * result + Objects.hashCode(value);
            return result;
        }

        /** {@inheritDoc} */
        @Override
        public String toString() {
            return "TestAggregateRootId[value=" + value + "]";
        }
    }

    /** \u6d4b\u8bd5\u4e13\u7528\u9886\u57df\u4e8b\u4ef6\uff0c\u7528\u4e8e\u63a7\u5236\u4e8b\u4ef6\u7c7b\u578b\u548c\u8d1f\u8f7d\u3002 */
    static final class TestEvent extends DomainEvent<TestAggregateRootId> {
        /** \u4f7f\u7528 <code>super()</code> \u521d\u59cb\u5316 <code>TestEvent</code> \u6d4b\u8bd5\u5939\u5177\u3002 */
        TestEvent() {
            super();
        }

        /**
         * \u4f7f\u7528 <code>super()</code> \u521d\u59cb\u5316 <code>TestEvent</code> \u6d4b\u8bd5\u5939\u5177\u3002
         * @param orderId \u6d4b\u8bd5\u4e2d\u8981\u5b9a\u4f4d\u7684\u8ba2\u5355\u6807\u8bc6
         */
        TestEvent(TestAggregateRootId orderId) {
            super(new EntityIdPath(orderId));
        }
    }
}
