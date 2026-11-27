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

    /** \u5728\u6d4b\u8bd5\u6846\u67b6\u4e2d\u6267\u884c <code>query.getWhereConditions()</code>\uff0c\u9a8c\u8bc1\u8be5\u573a\u666f\u3002 */
    @Test
    void nullOperatorsShouldBeRecordedWithoutValue() {
        OrderQuery query = new OrderQuery()
                .isNull(Order::getDeletedAt)
                .isNotNull(Order::getCreatedAt);

        assertThat(query.getWhereConditions())
                .extracting(LambdaCondition::operator)
                .containsExactly("IS_NULL", "IS_NOT_NULL");
    }

    /** \u9a8c\u8bc1<code>query.getWhereConditions()</code>\u4e3a\u7a7a\u3002 */
    @Test
    void valueOperatorShouldIgnoreNullValue() {
        OrderQuery query = new OrderQuery().eq(Order::getStatus, null);

        assertThat(query.getWhereConditions()).isEmpty();
    }

    /** \u5728\u6d4b\u8bd5\u6846\u67b6\u4e2d\u6267\u884c <code>query.getWhereConditions()</code>\u3001<code>org.assertj.core.groups.Tuple.tuple()</code>\uff0c\u9a8c\u8bc1\u8be5\u573a\u666f\u3002 */
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

    /** \u9a8c\u8bc1<code>condition.propertyRef().space()</code> \u7b49\u4e8e <code>PropertySpace.PERSISTENCE</code>\uff0c<code>condition.property()</code> \u7b49\u4e8e <code>"createdAt"</code>\uff0c<code>query.getSize()</code> \u7b49\u4e8e <code>20</code>\u3002 */
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

    /** \u8ba2\u5355\u6d4b\u8bd5\u67e5\u8be2\u5bf9\u8c61\uff0c\u4f9b\u6784\u9020\u4ed3\u50a8\u68c0\u7d22\u6761\u4ef6\u3002 */
    static final class OrderQuery extends Query<Order> {

        /**
         * \u901a\u8fc7 <code>eq()</code> \u53d6\u5f97\u5f53\u524d\u6d4b\u8bd5\u9700\u8981\u7684\u7ed3\u679c\u3002
         * @return \u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u7ed3\u679c\uff0c\u53d6\u81ea <code>eq(Order::getStatus, "PAID")</code>
         */
        OrderQuery paid() {
            return eq(Order::getStatus, "PAID");
        }

        /**
         * \u901a\u8fc7 <code>ge()</code> \u53d6\u5f97\u5f53\u524d\u6d4b\u8bd5\u9700\u8981\u7684\u7ed3\u679c\u3002
         * @param time \u4f20\u7ed9 <code>ge()</code> \u7684 <code>LocalDateTime</code> \u8f93\u5165
         * @return \u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u7ed3\u679c\uff0c\u53d6\u81ea <code>ge(Order::getCreatedAt, time)</code>
         */
        OrderQuery createdAfter(LocalDateTime time) {
            return ge(Order::getCreatedAt, time);
        }
    }

    /** \u4f9b\u6d4b\u8bd5\u9a8c\u8bc1\u4e8b\u4ef6\u5e94\u7528\u548c\u6301\u4e45\u5316\u7684\u805a\u5408\u6837\u672c\u3002 */
    static final class Order extends AggregateRoot<String> {

        /** \u6807\u8bc6\u5f53\u524d\u6d4b\u8bd5\u5bf9\u8c61\u7684\u4e3b\u952e\u6216\u4e8b\u4ef6\u6807\u8bc6\u3002 */
        private final String id;
        /** \u6d4b\u8bd5\u66ff\u8eab\u8fd4\u56de\u3001\u6a21\u62df\u6216\u6355\u83b7\u7684\u72b6\u6001\u3002 */
        private final String status;
        /** \u8bb0\u5f55\u521b\u5efa\u7684\u65f6\u95f4\u3002 */
        private final LocalDateTime createdAt;
        /** \u8bb0\u5f55\u88ab\u903b\u8f91\u5220\u9664\u7684\u65f6\u95f4\u3002 */
        private final LocalDateTime deletedAt;

        /**
         * \u7528\u7ed9\u5b9a\u503c\u521d\u59cb\u5316 <code>Order</code> \u5b9e\u4f8b\u3002
         * @param id <code>id</code> \u5b57\u6bb5
         * @param status <code>status</code> \u5b57\u6bb5
         * @param createdAt \u8bb0\u5f55\u521b\u5efa\u7684\u65f6\u95f4
         * @param deletedAt \u8bb0\u5f55\u88ab\u903b\u8f91\u5220\u9664\u7684\u65f6\u95f4
         */
        Order(String id, String status, LocalDateTime createdAt, LocalDateTime deletedAt) {
            this.id = id;
            this.status = status;
            this.createdAt = createdAt;
            this.deletedAt = deletedAt;
        }

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
        String getStatus() {
            return status;
        }

        /**
         * \u8bfb\u53d6\u8bb0\u5f55\u521b\u5efa\u7684\u65f6\u95f4\u3002
         * @return \u8bb0\u5f55\u521b\u5efa\u7684\u65f6\u95f4
         */
        LocalDateTime getCreatedAt() {
            return createdAt;
        }

        /**
         * \u8bfb\u53d6\u8bb0\u5f55\u88ab\u903b\u8f91\u5220\u9664\u7684\u65f6\u95f4\u3002
         * @return \u8bb0\u5f55\u88ab\u903b\u8f91\u5220\u9664\u7684\u65f6\u95f4
         */
        LocalDateTime getDeletedAt() {
            return deletedAt;
        }
    }

    /** \u6301\u4e45\u5316\u5c42\u4f7f\u7528\u7684\u8ba2\u5355\u6d4b\u8bd5\u6570\u636e\u6a21\u578b\u3002 */
    static final class OrderPO {

        /** \u5546\u54c1\u6216\u8ba2\u5355\u5408\u540c\u8981\u6c42\u68c0\u67e5\u7684\u8ba2\u5355\u72b6\u6001\u3002 */
        private String orderStatus;
        /** \u8bb0\u5f55\u521b\u5efa\u7684\u65f6\u95f4\u3002 */
        private LocalDateTime createdAt;
        /** \u8bb0\u5f55\u88ab\u903b\u8f91\u5220\u9664\u7684\u65f6\u95f4\u3002 */
        private LocalDateTime deletedAt;

        /**
         * \u8bfb\u53d6<code>orderStatus</code> \u5b57\u6bb5\u3002
         * @return <code>orderStatus</code> \u5b57\u6bb5
         */
        String getOrderStatus() {
            return orderStatus;
        }

        /**
         * \u8bfb\u53d6\u8bb0\u5f55\u521b\u5efa\u7684\u65f6\u95f4\u3002
         * @return \u8bb0\u5f55\u521b\u5efa\u7684\u65f6\u95f4
         */
        LocalDateTime getCreatedAt() {
            return createdAt;
        }

        /**
         * \u8bfb\u53d6\u8bb0\u5f55\u88ab\u903b\u8f91\u5220\u9664\u7684\u65f6\u95f4\u3002
         * @return \u8bb0\u5f55\u88ab\u903b\u8f91\u5220\u9664\u7684\u65f6\u95f4
         */
        LocalDateTime getDeletedAt() {
            return deletedAt;
        }
    }
}
