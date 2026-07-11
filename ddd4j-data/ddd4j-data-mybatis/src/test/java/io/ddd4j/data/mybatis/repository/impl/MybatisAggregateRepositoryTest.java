package io.ddd4j.data.mybatis.repository.impl;

import io.ddd4j.annotation.orm.DomainField;
import io.ddd4j.core.cqrs.query.Query;
import io.ddd4j.core.ddd.model.AggregateRoot;
import org.apache.ibatis.session.SqlSession;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

/**
 * 原生 MyBatis 属性空间翻译契约测试。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
class MybatisAggregateRepositoryTest {

    @Test
    void domainPropertyShouldMapThroughDomainMetadata() {
        TestRepository repository = new TestRepository(mock(SqlSession.class));
        OrderQuery query = new OrderQuery().eq(Order::status, "PAID");

        assertThat(repository.conditionColumns(query)).containsExactly("order_status");
    }

    @Test
    void persistenceScopeShouldMapPoPropertyAndOrder() {
        TestRepository repository = new TestRepository(mock(SqlSession.class));
        OrderQuery query = new OrderQuery();
        query.withPO(OrderPO.class)
                .eq(OrderPO::getOrderStatus, "PAID")
                .orderByDesc(OrderPO::getCreatedAt);

        assertThat(repository.conditionColumns(query)).containsExactly("order_status");
        assertThat(repository.orderColumns(query)).containsExactly("created_at");
    }

    @Test
    void persistenceScopeShouldRejectAnotherPoType() {
        TestRepository repository = new TestRepository(mock(SqlSession.class));
        OrderQuery query = new OrderQuery();
        query.withPO(OtherOrderPO.class).eq(OtherOrderPO::getOrderStatus, "PAID");

        assertThatThrownBy(() -> repository.conditionColumns(query))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("PERSISTENCE", OtherOrderPO.class.getName(), OrderPO.class.getName());
    }

    static final class TestRepository extends MybatisAggregateRepository<Order, OrderPO, String> {

        TestRepository(SqlSession sqlSession) {
            super(sqlSession, Order.class, OrderPO.class);
        }

        @SuppressWarnings("unchecked")
        List<String> conditionColumns(Query<Order> query) {
            List<Map<String, Object>> conditions =
                    (List<Map<String, Object>>) buildQueryParams(query).get("_lambdaConditions");
            return conditions.stream().map(condition -> (String) condition.get("column")).toList();
        }

        @SuppressWarnings("unchecked")
        List<String> orderColumns(Query<Order> query) {
            List<Map<String, Object>> orders =
                    (List<Map<String, Object>>) buildQueryParams(query).get("_lambdaOrderBy");
            return orders.stream().map(order -> (String) order.get("column")).toList();
        }

        @Override
        protected String mapperNamespace() {
            return "test.OrderMapper";
        }
    }

    static final class OrderQuery extends Query<Order> {
    }

    static final class Order extends AggregateRoot<String> {

        private String id;
        @DomainField(poField = "orderStatus")
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

        private String id;
        private String orderStatus;
        private String createdAt;

        String getOrderStatus() {
            return orderStatus;
        }

        String getCreatedAt() {
            return createdAt;
        }
    }

    static final class OtherOrderPO {

        private String orderStatus;

        String getOrderStatus() {
            return orderStatus;
        }
    }
}
