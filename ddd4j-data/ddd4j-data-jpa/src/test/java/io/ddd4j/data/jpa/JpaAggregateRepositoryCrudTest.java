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
import jakarta.persistence.NoResultException;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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

    /**
     * 执行 JPA 查询和持久化操作的实体管理器。
     */
    private EntityManager entityManager;
    /**
     * 用于当前读取或写入断言的测试仓储。
     */
    private TestRepository repository;

    /**
     * 测试执行前执行 <code>mock()</code> 完成资源准备。
     */
    @BeforeEach
    void setUp() {
        entityManager = mock(EntityManager.class);
        repository = new TestRepository(entityManager);
    }

    /**
     * 验证<code>repository</code>非空，<code>repository.getEntityManager()</code>非空。
     */
    @Test
    void repository_shouldReturnEntityManager() {
        // repository() 默认返回注入的 EntityManager
        // 通过内部实现：repository.persistenceProperty 等方法可访问
        assertNotNull(repository);
        assertNotNull(repository.getEntityManager());
    }

    /**
     * 验证<code>result</code>非空。
     */
    @Test
    void persistenceProperty_shouldDelegateToSuper() {
        // 子类未重写的属性走父类
        String result = repository.callPersistenceProperty("id");
        assertNotNull(result);
    }

    /**
     * 验证<code>result</code> 等于 <code>"orderStatus"</code>。
     */
    @Test
    void persistenceProperty_shouldUseOverride() {
        // 子类重写的属性
        String result = repository.callPersistenceProperty("status");
        assertEquals("orderStatus", result);
    }

    /**
     * 验证<code>em</code>非空，<code>em</code> 与…为同一实例 <code>entityManager</code>。
     */
    @Test
    void getEntityManager_shouldReturnInjectedInstance() {
        EntityManager em = repository.getEntityManager();
        assertNotNull(em);
        assertSame(entityManager, em);
    }

    /**
     * 验证<code>repository.findFirst()</code> 等于 <code>Optional.empty()</code>。
     */
    @Test
    void findFirstReturnsEmptyWhenTableHasNoRows() {
        TypedQuery<OrderPO> query = queryWithNoRows();
        when(entityManager.createQuery("SELECT p FROM OrderPO p", OrderPO.class)).thenReturn(query);

        assertEquals(Optional.empty(), repository.findFirst());
    }

    /**
     * 验证<code>repository.findFirst(new Query&lt;Order&gt;() { })</code> 等于 <code>Optional.empty()</code>。
     */
    @Test
    void findFirstWithCriteriaReturnsEmptyWhenNoRowMatches() {
        CriteriaBuilder builder = mock(CriteriaBuilder.class);
        @SuppressWarnings("unchecked")
        CriteriaQuery<OrderPO> criteria = mock(CriteriaQuery.class);
        when(entityManager.getCriteriaBuilder()).thenReturn(builder);
        when(builder.createQuery(OrderPO.class)).thenReturn(criteria);
        TypedQuery<OrderPO> query = queryWithNoRows();
        when(entityManager.createQuery(criteria)).thenReturn(query);

        assertEquals(Optional.empty(), repository.findFirst(new Query<Order>() {
        }));
    }

    /**
     * 读取 <code>query</code> 供当前测试使用。
     *
     * @return 当前测试使用的结果，取自 <code>query</code>
     */
    private TypedQuery<OrderPO> queryWithNoRows() {
        @SuppressWarnings("unchecked")
        TypedQuery<OrderPO> query = mock(TypedQuery.class);
        when(query.setMaxResults(1)).thenReturn(query);
        when(query.getResultList()).thenReturn(Collections.emptyList());
        when(query.getSingleResult()).thenThrow(new NoResultException("empty result"));
        return query;
    }

    // =================== 辅助类 ===================

    /**
     * 实现 <code>JpaAggregateRepository</code>，供测试调用 <code>getEntityManager()</code>、<code>callPersistenceProperty()</code>、<code>persistenceProperty()</code> 并观察结果。
     */
    static final class TestRepository extends JpaAggregateRepository<Order, OrderPO, String> {

        /**
         * 使用 <code>super()</code> 初始化 <code>TestRepository</code> 测试夹具。
         *
         * @param entityManager 用于初始化 <code>TestRepository</code> 的 <code>EntityManager</code> 值
         */
        TestRepository(EntityManager entityManager) {
            super(entityManager, Order.class, OrderPO.class);
        }

        /**
         * 读取 <code>this.entityManager</code> 供当前测试使用。
         *
         * @return 当前测试使用的结果，取自 <code>this.entityManager</code>
         */
        EntityManager getEntityManager() {
            return this.entityManager;
        }

        /**
         * 通过 <code>persistenceProperty()</code> 取得当前测试需要的结果。
         *
         * @param property 传给 <code>persistenceProperty()</code> 的 <code>String</code> 输入
         * @return 当前测试使用的结果，取自 <code>persistenceProperty(property)</code>
         */
        String callPersistenceProperty(String property) {
            return persistenceProperty(property);
        }

        /**
         * 将领域属性名映射为 JPA 持久化属性名。
         *
         * <p>测试夹具会调用 <code>"status".equals(domainProperty)</code>、<code>super.persistenceProperty(domainProperty)</code>。</p>
         *
         * @param domainProperty 领域属性名
         * @return 根据执行路径返回<code>"orderStatus"</code>、<code>super.persistenceProperty(domainProperty)</code>
         */
        @Override
        protected String persistenceProperty(String domainProperty) {
            if ("status".equals(domainProperty)) {
                return "orderStatus";
            }
            return super.persistenceProperty(domainProperty);
        }
    }

    /**
     * 供测试验证事件应用和持久化的聚合样本。
     */
    static final class Order extends AggregateRoot<String> {

        /**
         * 标识当前测试对象的主键或事件标识。
         */
        private String id;
        /**
         * 测试替身返回、模拟或捕获的状态。
         */
        private String status;

        /**
         * 读取<code>id</code> 字段。
         *
         * @return <code>id</code> 字段
         */
        @Override
        public String id() {
            return id;
        }

        /**
         * 读取<code>status</code> 字段。
         *
         * @return <code>status</code> 字段
         */
        public String status() {
            return status;
        }
    }

    /**
     * 持久化层使用的订单测试数据模型。
     */
    static final class OrderPO {
        /**
         * 商品或订单合同要求检查的订单状态。
         */
        private String orderStatus;

        /**
         * 读取<code>orderStatus</code> 字段。
         *
         * @return <code>orderStatus</code> 字段
         */
        public String getOrderStatus() {
            return orderStatus;
        }
    }
}
