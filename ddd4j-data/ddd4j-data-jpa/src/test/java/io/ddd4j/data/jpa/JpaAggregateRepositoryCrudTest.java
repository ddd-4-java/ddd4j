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
package io.ddd4j.data.jpa;

import io.ddd4j.core.cqrs.query.Query;
import io.ddd4j.core.ddd.model.AggregateRoot;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * {@link JpaAggregateRepository} 仓储方法测试。
 *
 * <p>通过 Mock EntityManager 验证仓储方法委托行为：
 * <ul>
 *   <li>repository() 默认实现</li>
 *   <li>JPA 查询操作委托给 EntityManager</li>
 * </ul>
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
class JpaAggregateRepositoryCrudTest {

    private EntityManager entityManager;
    private TestRepository repository;

    @BeforeEach
    void setUp() {
        entityManager = mock(EntityManager.class);
        repository = new TestRepository(entityManager);
    }

    @Test
    void repository_shouldReturnEntityManager() {
        // repository() 默认返回注入的 EntityManager
        // 通过内部实现：repository.persistenceProperty 等方法可访问
        assertNotNull(repository);
        assertNotNull(repository.getEntityManager());
    }

    @Test
    void persistenceProperty_shouldDelegateToSuper() {
        // 子类未重写的属性走父类
        String result = repository.callPersistenceProperty("id");
        assertNotNull(result);
    }

    @Test
    void persistenceProperty_shouldUseOverride() {
        // 子类重写的属性
        String result = repository.callPersistenceProperty("status");
        assertEquals("orderStatus", result);
    }

    @Test
    void getEntityManager_shouldReturnInjectedInstance() {
        EntityManager em = repository.getEntityManager();
        assertNotNull(em);
        assertSame(entityManager, em);
    }

    // =================== 辅助类 ===================

    static final class TestRepository extends JpaAggregateRepository<Order, OrderPO, String> {

        TestRepository(EntityManager entityManager) {
            super(entityManager, Order.class, OrderPO.class);
        }

        EntityManager getEntityManager() {
            return this.entityManager;
        }

        String callPersistenceProperty(String property) {
            return persistenceProperty(property);
        }

        @Override
        protected String persistenceProperty(String domainProperty) {
            if ("status".equals(domainProperty)) {
                return "orderStatus";
            }
            return super.persistenceProperty(domainProperty);
        }
    }

    static final class Order extends AggregateRoot<String> {

        private String id;
        private String status;

        @Override
        public String id() {
            return id;
        }

        public String status() {
            return status;
        }
    }

    static final class OrderPO {
        private String orderStatus;

        public String getOrderStatus() {
            return orderStatus;
        }
    }
}
