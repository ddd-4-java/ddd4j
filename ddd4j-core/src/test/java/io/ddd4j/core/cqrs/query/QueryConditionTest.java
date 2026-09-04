package io.ddd4j.core.cqrs.query;

import io.ddd4j.core.ddd.model.AggregateRoot;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link Query} ORM 无关条件模型测试。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
class QueryConditionTest {

    @Test
    void nullOperatorsShouldBeRecordedWithoutValue() {
        OrderQuery query = new OrderQuery()
                .isNull(Order::getDeletedAt)
                .isNotNull(Order::getCreatedAt);

        assertThat(query.getWhereConditions())
                .extracting(LambdaCondition::operator)
                .containsExactly("IS_NULL", "IS_NOT_NULL");
    }

    @Test
    void valueOperatorShouldIgnoreNullValue() {
        OrderQuery query = new OrderQuery().eq(Order::getStatus, null);

        assertThat(query.getWhereConditions()).isEmpty();
    }

    @Test
    void richQueryMethodShouldComposeDomainConditions() {
        OrderQuery query = new OrderQuery().paid().createdAfter(LocalDateTime.of(2026, 1, 1, 0, 0));

        assertThat(query.getWhereConditions())
                .extracting(LambdaCondition::property, LambdaCondition::operator, LambdaCondition::value)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("status", "=", "PAID"),
                        org.assertj.core.groups.Tuple.tuple("createdAt", ">=", LocalDateTime.of(2026, 1, 1, 0, 0))
                );
    }

    @Test
    void persistenceScopeShouldKeepPoPropertySeparateFromDomainModel() {
        OrderQuery query = new OrderQuery();

        query.withPO(OrderPO.class)
                .eq(OrderPO::getOrderStatus, "PAID")
                .isNull(OrderPO::getDeletedAt)
                .orderByDesc(OrderPO::getCreatedAt)
                .current(2)
                .size(20);

        assertThat(query.getWhereConditions())
                .extracting(condition -> condition.propertyRef().space(),
                        LambdaCondition::property,
                        condition -> condition.propertyRef().ownerType())
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(PropertySpace.PERSISTENCE, "orderStatus", OrderPO.class),
                        org.assertj.core.groups.Tuple.tuple(PropertySpace.PERSISTENCE, "deletedAt", OrderPO.class));
        assertThat(query.getOrderByConditions()).singleElement().satisfies(condition -> {
            assertThat(condition.propertyRef().space()).isEqualTo(PropertySpace.PERSISTENCE);
            assertThat(condition.property()).isEqualTo("createdAt");
        });
        assertThat(query.getCurrent()).isEqualTo(2);
        assertThat(query.getSize()).isEqualTo(20);
    }

    static final class OrderQuery extends Query<Order> {

        OrderQuery paid() {
            return eq(Order::getStatus, "PAID");
        }

        OrderQuery createdAfter(LocalDateTime time) {
            return ge(Order::getCreatedAt, time);
        }
    }

    static final class Order extends AggregateRoot<String> {

        private final String id;
        private final String status;
        private final LocalDateTime createdAt;
        private final LocalDateTime deletedAt;

        Order(String id, String status, LocalDateTime createdAt, LocalDateTime deletedAt) {
            this.id = id;
            this.status = status;
            this.createdAt = createdAt;
            this.deletedAt = deletedAt;
        }

        @Override
        public String id() {
            return id;
        }

        String getStatus() {
            return status;
        }

        LocalDateTime getCreatedAt() {
            return createdAt;
        }

        LocalDateTime getDeletedAt() {
            return deletedAt;
        }
    }

    static final class OrderPO {

        private String orderStatus;
        private LocalDateTime createdAt;
        private LocalDateTime deletedAt;

        String getOrderStatus() {
            return orderStatus;
        }

        LocalDateTime getCreatedAt() {
            return createdAt;
        }

        LocalDateTime getDeletedAt() {
            return deletedAt;
        }
    }
}
