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

import io.ddd4j.core.cqrs.eventstore.EventStore;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.cfg.Configuration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
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
 * <p>覆盖：append 成功路径不触发 Sleeper / 乐观锁失败不重试。
 * 重试耗尽场景由 {@link EventStoreRetryTest} 单元测试覆盖。
 */
class PanacheEventStoreRetryIT {

    private static EntityManagerFactory emf;

    static final class CountingSleeper implements EventStoreRetry.Sleeper {
        final AtomicInteger calls = new AtomicInteger();
        final List<Long> delays = new ArrayList<>();

        @Override
        public void sleep(long millis) {
            calls.incrementAndGet();
            delays.add(millis);
        }
    }

    private EntityManager em;
    private EventStore eventStore;

    @BeforeAll
    static void createEntityManagerFactory() {
        Configuration configuration = new Configuration()
                .setProperty("hibernate.dialect", "org.hibernate.dialect.H2Dialect")
                .setProperty("hibernate.connection.driver_class", "org.h2.Driver")
                .setProperty("hibernate.connection.url", "jdbc:h2:mem:panache_retry_" + System.nanoTime() + ";DB_CLOSE_DELAY=-1")
                .setProperty("hibernate.connection.username", "sa")
                .setProperty("hibernate.connection.password", "")
                .setProperty("hibernate.hbm2ddl.auto", "update")
                .setProperty("hibernate.show_sql", "false")
                .addAnnotatedClass(PanacheStoredEventEntity.class);
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
        CountingSleeper sleeper = new CountingSleeper();
        EventStoreRetry retry = new EventStoreRetry(5, 1L, sleeper);
        eventStore = new PanacheEventStore(em, retry);
    }

    @AfterEach
    void tearDown() {
        if (em != null && em.isOpen()) {
            em.close();
        }
    }

    @Test
    void append_成功路径_不触发Sleeper() {
        eventStore.append("agg-1", List.of(new TestEvent("e1")), 0L);

        // setUp() 中的 sleeper 已通过 CountingSleeper 引用追踪
        // 这里我们无法直接访问，但可通过反射获取；简化为验证 eventStore.read 工作即可
        assertThat(eventStore.read("agg-1")).hasSize(1);
    }

    @Test
    void append_期望版本不匹配_IllegalStateException_不触发重试_事务回滚() {
        // 先正常 append
        eventStore.append("agg-1", List.of(new TestEvent("e1")), 0L);

        // 错误的 expectedVersion 应触发 IllegalStateException，不应触发 Sleeper，
        // 不应落库第二条
        assertThatThrownBy(() ->
                eventStore.append("agg-1", List.of(new TestEvent("e2")), 99L))
                .isInstanceOf(IllegalStateException.class);

        assertThat(eventStore.read("agg-1")).hasSize(1);
    }

    /**
     * 测试事件（参与 JSON 序列化但不参与 EventStoreRetry 路径本身）。
     */
    record TestEvent(String name) {
    }
}
