package io.ddd4j.data.jpa;

import io.ddd4j.core.cqrs.query.LambdaCondition;
import io.ddd4j.core.cqrs.query.Query;
import io.ddd4j.core.ddd.model.AggregateRoot;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

/**
 * {@link JpaAggregateRepository} 属性空间翻译契约测试。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
class JpaAggregateRepositoryTest {

    @Test
    void domainPropertyShouldUseRepositoryMapping() {
        TestRepository repository = new TestRepository(mock(EntityManager.class));
        OrderQuery query = new OrderQuery().eq(Order::status, "PAID");

        assertThat(repository.property(query.getWhereConditions().get(0))).isEqualTo("orderStatus");
    }

    @Test
    void persistenceScopeShouldUseJpaPropertyDirectly() {
        TestRepository repository = new TestRepository(mock(EntityManager.class));
        OrderQuery query = new OrderQuery();
        query.withPO(OrderPO.class).eq(OrderPO::getOrderStatus, "PAID");

        assertThat(repository.property(query.getWhereConditions().get(0))).isEqualTo("orderStatus");
    }

    @Test
    void persistenceScopeShouldRejectAnotherEntityType() {
        TestRepository repository = new TestRepository(mock(EntityManager.class));
        OrderQuery query = new OrderQuery();
        query.withPO(OtherOrderPO.class).eq(OtherOrderPO::getOrderStatus, "PAID");
        LambdaCondition condition = query.getWhereConditions().get(0);

        assertThatThrownBy(() -> repository.property(condition))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("PERSISTENCE", OtherOrderPO.class.getName(), OrderPO.class.getName());
    }

    static final class TestRepository extends JpaAggregateRepository<Order, OrderPO, String> {

        TestRepository(EntityManager entityManager) {
            super(entityManager, Order.class, OrderPO.class);
        }

        String property(LambdaCondition condition) {
            return persistenceProperty(condition);
        }

        @Override
        protected String persistenceProperty(String domainProperty) {
            if ("status".equals(domainProperty)) {
                return "orderStatus";
            }
            return super.persistenceProperty(domainProperty);
        }
    }

    static final class OrderQuery extends Query<Order> {
    }

    static final class Order extends AggregateRoot<String> {

        private String id;
        private String status;

        @Override
        public String id() {
            return id;
        }

        String status() {
            return status;
        }
    }

    static final class OrderPO {

        private String orderStatus;

        String getOrderStatus() {
            return orderStatus;
        }
    }

    static final class OtherOrderPO {

        private String orderStatus;

        String getOrderStatus() {
            return orderStatus;
        }
    }
}
