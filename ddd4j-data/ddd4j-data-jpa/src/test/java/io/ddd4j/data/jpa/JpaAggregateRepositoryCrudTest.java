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

    /** \u6267\u884c JPA \u67e5\u8be2\u548c\u6301\u4e45\u5316\u64cd\u4f5c\u7684\u5b9e\u4f53\u7ba1\u7406\u5668\u3002 */
    private EntityManager entityManager;
    /** \u7528\u4e8e\u5f53\u524d\u8bfb\u53d6\u6216\u5199\u5165\u65ad\u8a00\u7684\u6d4b\u8bd5\u4ed3\u50a8\u3002 */
    private TestRepository repository;

    /** \u6d4b\u8bd5\u6267\u884c\u524d\u6267\u884c <code>mock()</code> \u5b8c\u6210\u8d44\u6e90\u51c6\u5907\u3002 */
    @BeforeEach
    void setUp() {
        entityManager = mock(EntityManager.class);
        repository = new TestRepository(entityManager);
    }

    /** \u9a8c\u8bc1<code>repository</code>\u975e\u7a7a\uff0c<code>repository.getEntityManager()</code>\u975e\u7a7a\u3002 */
    @Test
    void repository_shouldReturnEntityManager() {
        // repository() 默认返回注入的 EntityManager
        // 通过内部实现：repository.persistenceProperty 等方法可访问
        assertNotNull(repository);
        assertNotNull(repository.getEntityManager());
    }

    /** \u9a8c\u8bc1<code>result</code>\u975e\u7a7a\u3002 */
    @Test
    void persistenceProperty_shouldDelegateToSuper() {
        // 子类未重写的属性走父类
        String result = repository.callPersistenceProperty("id");
        assertNotNull(result);
    }

    /** \u9a8c\u8bc1<code>result</code> \u7b49\u4e8e <code>"orderStatus"</code>\u3002 */
    @Test
    void persistenceProperty_shouldUseOverride() {
        // 子类重写的属性
        String result = repository.callPersistenceProperty("status");
        assertEquals("orderStatus", result);
    }

    /** \u9a8c\u8bc1<code>em</code>\u975e\u7a7a\uff0c<code>em</code> \u4e0e\u2026\u4e3a\u540c\u4e00\u5b9e\u4f8b <code>entityManager</code>\u3002 */
    @Test
    void getEntityManager_shouldReturnInjectedInstance() {
        EntityManager em = repository.getEntityManager();
        assertNotNull(em);
        assertSame(entityManager, em);
    }

    // =================== 辅助类 ===================

    /** \u5b9e\u73b0 <code>JpaAggregateRepository</code>\uff0c\u4f9b\u6d4b\u8bd5\u8c03\u7528 <code>getEntityManager()</code>\u3001<code>callPersistenceProperty()</code>\u3001<code>persistenceProperty()</code> \u5e76\u89c2\u5bdf\u7ed3\u679c\u3002 */
    static final class TestRepository extends JpaAggregateRepository<Order, OrderPO, String> {

        /**
         * \u4f7f\u7528 <code>super()</code> \u521d\u59cb\u5316 <code>TestRepository</code> \u6d4b\u8bd5\u5939\u5177\u3002
         * @param entityManager \u7528\u4e8e\u521d\u59cb\u5316 <code>TestRepository</code> \u7684 <code>EntityManager</code> \u503c
         */
        TestRepository(EntityManager entityManager) {
            super(entityManager, Order.class, OrderPO.class);
        }

        /**
         * \u8bfb\u53d6 <code>this.entityManager</code> \u4f9b\u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u3002
         * @return \u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u7ed3\u679c\uff0c\u53d6\u81ea <code>this.entityManager</code>
         */
        EntityManager getEntityManager() {
            return this.entityManager;
        }

        /**
         * \u901a\u8fc7 <code>persistenceProperty()</code> \u53d6\u5f97\u5f53\u524d\u6d4b\u8bd5\u9700\u8981\u7684\u7ed3\u679c\u3002
         * @param property \u4f20\u7ed9 <code>persistenceProperty()</code> \u7684 <code>String</code> \u8f93\u5165
         * @return \u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u7ed3\u679c\uff0c\u53d6\u81ea <code>persistenceProperty(property)</code>
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

    /** \u4f9b\u6d4b\u8bd5\u9a8c\u8bc1\u4e8b\u4ef6\u5e94\u7528\u548c\u6301\u4e45\u5316\u7684\u805a\u5408\u6837\u672c\u3002 */
    static final class Order extends AggregateRoot<String> {

        /** \u6807\u8bc6\u5f53\u524d\u6d4b\u8bd5\u5bf9\u8c61\u7684\u4e3b\u952e\u6216\u4e8b\u4ef6\u6807\u8bc6\u3002 */
        private String id;
        /** \u6d4b\u8bd5\u66ff\u8eab\u8fd4\u56de\u3001\u6a21\u62df\u6216\u6355\u83b7\u7684\u72b6\u6001\u3002 */
        private String status;

        /**
         * \u8bfb\u53d6<code>id</code> \u5b57\u6bb5\u3002
         * @return <code>id</code> \u5b57\u6bb5
         */
        @Override
        public String id() {
            return id;
        }

        /**
         * \u8bfb\u53d6<code>status</code> \u5b57\u6bb5\u3002
         * @return <code>status</code> \u5b57\u6bb5
         */
        public String status() {
            return status;
        }
    }

    /** \u6301\u4e45\u5316\u5c42\u4f7f\u7528\u7684\u8ba2\u5355\u6d4b\u8bd5\u6570\u636e\u6a21\u578b\u3002 */
    static final class OrderPO {
        /** \u5546\u54c1\u6216\u8ba2\u5355\u5408\u540c\u8981\u6c42\u68c0\u67e5\u7684\u8ba2\u5355\u72b6\u6001\u3002 */
        private String orderStatus;

        /**
         * \u8bfb\u53d6<code>orderStatus</code> \u5b57\u6bb5\u3002
         * @return <code>orderStatus</code> \u5b57\u6bb5
         */
        public String getOrderStatus() {
            return orderStatus;
        }
    }
}
