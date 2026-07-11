package io.ddd4j.data.mybatis.repository;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.ddd4j.core.cqrs.query.Query;
import io.ddd4j.core.ddd.model.AggregateRoot;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

/**
 * {@link MybatisAggregateRepository} 查询翻译契约测试。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
class MybatisAggregateRepositoryTest {

    @Test
    void domainQueryShouldUseOrmNeutralConditions() {
        TestRepository repository = new TestRepository(mock(OrderMapper.class));
        OrderQuery query = new OrderQuery()
                .eq(Order::getStatus, "PAID")
                .isNull(Order::getDeletedAt);

        QueryWrapper<OrderPO> wrapper = repository.wrapper(query);

        assertThat(wrapper.getCustomSqlSegment())
                .contains("order_status", "deleted_at", "IS NULL");
    }

    @Test
    void persistenceScopeShouldUsePoMetadataWithoutChangingDomainModel() {
        TestRepository repository = new TestRepository(mock(OrderMapper.class));
        OrderQuery query = new OrderQuery();
        query.withPO(OrderPO.class)
                .eq(OrderPO::getStatus, "PAID")
                .isNull(OrderPO::getDeletedAt)
                .orderByDesc(OrderPO::getDeletedAt);

        QueryWrapper<OrderPO> wrapper = repository.wrapper(query);

        assertThat(wrapper.getCustomSqlSegment())
                .contains("order_status", "deleted_at", "IS NULL", "ORDER BY");
    }

    @Test
    void persistenceScopeShouldRejectPoFromAnotherRepository() {
        TestRepository repository = new TestRepository(mock(OrderMapper.class));
        OrderQuery query = new OrderQuery();
        query.withPO(OtherOrderPO.class).eq(OtherOrderPO::getStatus, "PAID");

        assertThatThrownBy(() -> repository.wrapper(query))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("PERSISTENCE", OtherOrderPO.class.getName(), OrderPO.class.getName());
    }

    static final class TestRepository
            extends MybatisAggregateRepository<OrderMapper, Order, OrderPO, OrderQuery, String> {

        TestRepository(OrderMapper mapper) {
            super(mapper, Order.class, OrderPO.class);
        }

        QueryWrapper<OrderPO> wrapper(Query<Order> query) {
            return getBaseWrapper(query);
        }
    }

    interface OrderMapper extends BaseMapper<OrderPO> {
    }

    static final class OrderQuery extends Query<Order> {
    }

    static final class Order extends AggregateRoot<String> {

        private String id;
        private String status;
        private LocalDateTime deletedAt;

        @Override
        public String id() {
            return id;
        }

        String getStatus() {
            return status;
        }

        LocalDateTime getDeletedAt() {
            return deletedAt;
        }
    }

    @TableName("orders")
    static final class OrderPO {

        @TableId
        private String id;

        @TableField("order_status")
        private String status;

        @TableField("deleted_at")
        private LocalDateTime deletedAt;

        String getStatus() {
            return status;
        }

        LocalDateTime getDeletedAt() {
            return deletedAt;
        }
    }

    static final class OtherOrderPO {

        private String status;

        String getStatus() {
            return status;
        }
    }
}
