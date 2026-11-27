package io.ddd4j.data.mybatis.repository;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.ddd4j.core.api.R;
import io.ddd4j.core.cqrs.query.Query;
import io.ddd4j.core.ddd.model.AggregateRoot;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * {@link MybatisAggregateRepository} CRUD 操作单元测试。
 *
 * <p>通过 Mock BaseMapper 验证：
 * <ul>
 *   <li>findById - 按 ID 查询</li>
 *   <li>save - 插入/更新</li>
 *   <li>deleteById - 按 ID 删除</li>
 *   <li>count / findAll - 计数与全量查询</li>
 *   <li>findList / findFirst / page - 条件查询</li>
 *   <li>existsById - 存在判断</li>
 *   <li>toModel / toPersistenceObject - PO↔Domain 映射</li>
 * </ul>
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
class MybatisAggregateRepositoryCrudTest {

    /** \u5f53\u524d\u88ab\u9a8c\u8bc1\u6216\u66ff\u6362\u7684\u6301\u4e45\u5316 Mapper \u5b9e\u4f8b\u3002 */
    private OrderMapper mapper;
    /** \u88ab\u6d4b\u4ed3\u50a8\u6216\u7528\u4e8e\u63d0\u4f9b\u6d4b\u8bd5\u6570\u636e\u7684\u4ed3\u50a8\u5b9e\u4f8b\u3002 */
    private TestRepository repository;

    /** \u6d4b\u8bd5\u524d\u4f9d\u6b21\u521d\u59cb\u5316<code>repository</code>\u3002 */
    @BeforeEach
    void setUp() {
        mapper = mock(OrderMapper.class);
        repository = new TestRepository(mapper);
    }

    // =================== findById ===================

    /** \u9a8c\u8bc1<code>result</code>\u5b58\u5728\u503c\uff0c<code>result.get().id()</code> \u7b49\u4e8e <code>"1"</code>\uff0c<code>result.get().getStatus()</code> \u7b49\u4e8e <code>"PAID"</code>\u3002 */
    @Test
    void findById_shouldReturnModelWhenExists() {
        OrderPO po = new OrderPO("1", "PAID", null);
        when(mapper.selectById("1")).thenReturn(po);

        Optional<Order> result = repository.findById("1");

        assertThat(result).isPresent();
        assertThat(result.get().id()).isEqualTo("1");
        assertThat(result.get().getStatus()).isEqualTo("PAID");
    }

    /** \u9a8c\u8bc1<code>result</code>\u4e3a\u7a7a\u3002 */
    @Test
    void findById_shouldReturnEmptyWhenNotExists() {
        when(mapper.selectById("999")).thenReturn(null);

        Optional<Order> result = repository.findById("999");

        assertThat(result).isEmpty();
    }

    /** \u9a8c\u8bc1<code>result</code>\u4e3a\u7a7a\u3002 */
    @Test
    void findById_shouldReturnEmptyWhenIdIsNull() {
        Optional<Order> result = repository.findById(null);

        assertThat(result).isEmpty();
        verify(mapper, never()).selectById(any());
    }

    // =================== existsById ===================

    /** \u9a8c\u8bc1<code>result</code>\u4e3a\u771f\u3002 */
    @Test
    void existsById_shouldReturnTrueWhenExists() {
        when(mapper.selectById("1")).thenReturn(new OrderPO("1", "PAID", null));

        boolean result = repository.existsById("1");

        assertThat(result).isTrue();
    }

    /** \u9a8c\u8bc1<code>result</code>\u4e3a\u5047\u3002 */
    @Test
    void existsById_shouldReturnFalseWhenNotExists() {
        when(mapper.selectById("999")).thenReturn(null);

        boolean result = repository.existsById("999");

        assertThat(result).isFalse();
    }

    /** \u9a8c\u8bc1<code>result</code>\u4e3a\u5047\u3002 */
    @Test
    void existsById_shouldReturnFalseWhenIdIsNull() {
        boolean result = repository.existsById(null);

        assertThat(result).isFalse();
    }

    // =================== save ===================

    /** \u9a8c\u8bc1\u8c03\u7528\u4e86 <code>mapper.insert(any(OrderPO.class))</code>\uff0c\u6ca1\u6709\u8c03\u7528 <code>mapper.updateById(any(OrderPO.class))</code>\u3002 */
    @Test
    void save_shouldInsertWhenNew() {
        Order order = new Order(null, "DRAFT", null);
        when(mapper.selectById(any(Serializable.class))).thenReturn(null);
        when(mapper.insert(any(OrderPO.class))).thenReturn(1);

        Order saved = repository.save(order);

        verify(mapper).insert(any(OrderPO.class));
        verify(mapper, never()).updateById(any(OrderPO.class));
    }

    /** \u9a8c\u8bc1\u8c03\u7528\u4e86 <code>mapper.updateById(any(OrderPO.class))</code>\uff0c\u6ca1\u6709\u8c03\u7528 <code>mapper.insert(any(OrderPO.class))</code>\u3002 */
    @Test
    void save_shouldUpdateWhenExists() {
        Order order = new Order("1", "PAID", null);
        when(mapper.selectById("1")).thenReturn(new OrderPO("1", "DRAFT", null));
        when(mapper.updateById(any(OrderPO.class))).thenReturn(1);

        Order saved = repository.save(order);

        verify(mapper).updateById(any(OrderPO.class));
        verify(mapper, never()).insert(any(OrderPO.class));
    }

    // =================== deleteById ===================

    /** \u9a8c\u8bc1\u8c03\u7528\u4e86 <code>mapper.deleteById(eq("1"))</code>\u3002 */
    @Test
    void deleteById_shouldCallMapper() {
        repository.deleteById("1");

        verify(mapper).deleteById(eq("1"));
    }

    /** \u9a8c\u8bc1\u6ca1\u6709\u8c03\u7528 <code>mapper.deleteById(any(Serializable.class))</code>\u3002 */
    @Test
    void deleteById_shouldNotCallMapperWhenIdIsNull() {
        repository.deleteById(null);

        verify(mapper, never()).deleteById(any(Serializable.class));
    }

    // =================== count ===================

    /** \u9a8c\u8bc1<code>result</code> \u7b49\u4e8e <code>5L</code>\u3002 */
    @Test
    void count_shouldReturnCount() {
        when(mapper.selectCount(any(QueryWrapper.class))).thenReturn(5L);

        long result = repository.count();

        assertThat(result).isEqualTo(5L);
    }

    /** \u9a8c\u8bc1<code>result</code> \u7b49\u4e8e <code>0L</code>\u3002 */
    @Test
    void count_shouldReturnZeroWhenNull() {
        when(mapper.selectCount(any(QueryWrapper.class))).thenReturn(null);

        long result = repository.count();

        assertThat(result).isEqualTo(0L);
    }

    // =================== findAll ===================

    /** \u9a8c\u8bc1<code>result</code> \u7684\u5927\u5c0f\u4e3a <code>2</code>\uff0c<code>result.get(0).id()</code> \u7b49\u4e8e <code>"1"</code>\uff0c<code>result.get(1).id()</code> \u7b49\u4e8e <code>"2"</code>\u3002 */
    @Test
    void findAll_shouldReturnAllModels() {
        List<OrderPO> poList = List.of(
                new OrderPO("1", "PAID", null),
                new OrderPO("2", "DRAFT", null)
        );
        when(mapper.selectList(any(QueryWrapper.class))).thenReturn(poList);

        List<Order> result = repository.findAll();

        assertThat(result).hasSize(2);
        assertThat(result.get(0).id()).isEqualTo("1");
        assertThat(result.get(1).id()).isEqualTo("2");
    }

    /** \u9a8c\u8bc1<code>result</code>\u4e3a\u7a7a\u3002 */
    @Test
    void findAll_shouldReturnEmptyListWhenNoRecords() {
        when(mapper.selectList(any(QueryWrapper.class))).thenReturn(Collections.emptyList());

        List<Order> result = repository.findAll();

        assertThat(result).isEmpty();
    }

    // =================== findFirst ===================

    /** \u9a8c\u8bc1<code>result</code>\u5b58\u5728\u503c\uff0c<code>result.get().id()</code> \u7b49\u4e8e <code>"1"</code>\u3002 */
    @Test
    void findFirst_shouldReturnFirstModel() {
        OrderPO po = new OrderPO("1", "PAID", null);
        when(mapper.selectList(any(QueryWrapper.class))).thenReturn(List.of(po));

        Optional<Order> result = repository.findFirst();

        assertThat(result).isPresent();
        assertThat(result.get().id()).isEqualTo("1");
    }

    /** \u9a8c\u8bc1<code>result</code>\u4e3a\u7a7a\u3002 */
    @Test
    void findFirst_shouldReturnEmptyWhenNoRecords() {
        when(mapper.selectList(any(QueryWrapper.class))).thenReturn(Collections.emptyList());

        Optional<Order> result = repository.findFirst();

        assertThat(result).isEmpty();
    }

    // =================== toModel / toPersistenceObject ===================

    /** \u9a8c\u8bc1<code>model</code>\u975e\u7a7a\uff0c<code>model.id()</code> \u7b49\u4e8e <code>"1"</code>\uff0c<code>model.getStatus()</code> \u7b49\u4e8e <code>"PAID"</code>\u3002 */
    @Test
    void toModel_shouldConvertPoToModel() {
        OrderPO po = new OrderPO("1", "PAID", LocalDateTime.of(2026, 1, 1, 0, 0));

        Order model = repository.toModel(po);

        assertThat(model).isNotNull();
        assertThat(model.id()).isEqualTo("1");
        assertThat(model.getStatus()).isEqualTo("PAID");
    }

    /** \u9a8c\u8bc1<code>model</code>\u4e3a\u7a7a\u3002 */
    @Test
    void toModel_shouldReturnNullWhenPoIsNull() {
        Order model = repository.toModel(null);

        assertThat(model).isNull();
    }

    /** \u9a8c\u8bc1<code>po</code>\u975e\u7a7a\uff0c<code>po.getId()</code> \u7b49\u4e8e <code>"1"</code>\uff0c<code>po.getStatus()</code> \u7b49\u4e8e <code>"PAID"</code>\u3002 */
    @Test
    void toPersistenceObject_shouldConvertModelToPo() {
        Order model = new Order("1", "PAID", null);

        OrderPO po = repository.toPersistenceObject(model);

        assertThat(po).isNotNull();
        assertThat(po.getId()).isEqualTo("1");
        assertThat(po.getStatus()).isEqualTo("PAID");
    }

    /** \u9a8c\u8bc1<code>po</code>\u4e3a\u7a7a\u3002 */
    @Test
    void toPersistenceObject_shouldReturnNullWhenModelIsNull() {
        OrderPO po = repository.toPersistenceObject(null);

        assertThat(po).isNull();
    }

    // =================== 辅助类 ===================

    /** \u4f9b\u6d4b\u8bd5\u9a8c\u8bc1\u6cdb\u578b\u89e3\u6790\u548c\u4ed3\u50a8\u64cd\u4f5c\u7684\u5177\u4f53\u4ed3\u50a8\u5b9e\u73b0\u3002 */
    static final class TestRepository
            extends MybatisAggregateRepository<OrderMapper, Order, OrderPO, OrderQuery, String> {

        /**
         * \u4f7f\u7528 <code>super()</code> \u521d\u59cb\u5316 <code>TestRepository</code> \u6d4b\u8bd5\u5939\u5177\u3002
         * @param mapper \u5f53\u524d\u6d4b\u8bd5\u767b\u8bb0\u6216\u4f7f\u7528\u7684\u6301\u4e45\u5316\u6620\u5c04\u7ec4\u4ef6
         */
        TestRepository(OrderMapper mapper) {
            super(mapper, Order.class, OrderPO.class);
        }
    }

    /** \u4e3a\u8ba2\u5355\u6301\u4e45\u5316\u6d4b\u8bd5\u63d0\u4f9b\u7684\u6620\u5c04\u5165\u53e3\u3002 */
    interface OrderMapper extends BaseMapper<OrderPO> {
    }

    /** \u8ba2\u5355\u6d4b\u8bd5\u67e5\u8be2\u5bf9\u8c61\uff0c\u4f9b\u6784\u9020\u4ed3\u50a8\u68c0\u7d22\u6761\u4ef6\u3002 */
    static final class OrderQuery extends Query<Order> {
    }

    /** \u4f9b\u6d4b\u8bd5\u9a8c\u8bc1\u4e8b\u4ef6\u5e94\u7528\u548c\u6301\u4e45\u5316\u7684\u805a\u5408\u6837\u672c\u3002 */
    static final class Order extends AggregateRoot<String> {

        /** \u7528\u4e8e\u6784\u9020\u548c\u5b9a\u4f4d\u6d4b\u8bd5\u8bb0\u5f55\u7684\u4e3b\u952e\u3002 */
        private String id;
        /** \u6d4b\u8bd5\u5bf9\u8c61\u5f53\u524d\u7684\u4e1a\u52a1\u72b6\u6001\u3002 */
        private String status;
        /** \u8bb0\u5f55\u88ab\u903b\u8f91\u5220\u9664\u7684\u65f6\u95f4\u3002 */
        private LocalDateTime deletedAt;

        /** \u521b\u5efa\u5b57\u6bb5\u4f7f\u7528\u9ed8\u8ba4\u521d\u59cb\u503c\u7684 <code>Order</code> \u6d4b\u8bd5\u5bf9\u8c61\u3002 */
        Order() {
        }

        /**
         * \u7528\u7ed9\u5b9a\u503c\u521d\u59cb\u5316 <code>Order</code> \u5b9e\u4f8b\u3002
         * @param id <code>id</code> \u5b57\u6bb5
         * @param status <code>status</code> \u5b57\u6bb5
         * @param deletedAt \u8bb0\u5f55\u88ab\u903b\u8f91\u5220\u9664\u7684\u65f6\u95f4
         */
        Order(String id, String status, LocalDateTime deletedAt) {
            this.id = id;
            this.status = status;
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
         * \u8bfb\u53d6<code>id</code> \u5b57\u6bb5\u3002
         * @return <code>id</code> \u5b57\u6bb5
         */
        public String getId() {
            return id;
        }

        /**
         * \u8bbe\u7f6e<code>id</code> \u5b57\u6bb5\u3002
         * @param id \u8981\u5199\u5165\u7684\u503c
         */
        public void setId(String id) {
            this.id = id;
        }

        /**
         * \u8bfb\u53d6<code>status</code> \u5b57\u6bb5\u3002
         * @return <code>status</code> \u5b57\u6bb5
         */
        String getStatus() {
            return status;
        }

        /**
         * \u8bbe\u7f6e<code>status</code> \u5b57\u6bb5\u3002
         * @param status \u8981\u5199\u5165\u7684\u503c
         */
        public void setStatus(String status) {
            this.status = status;
        }

        /**
         * \u8bfb\u53d6\u8bb0\u5f55\u88ab\u903b\u8f91\u5220\u9664\u7684\u65f6\u95f4\u3002
         * @return \u8bb0\u5f55\u88ab\u903b\u8f91\u5220\u9664\u7684\u65f6\u95f4
         */
        LocalDateTime getDeletedAt() {
            return deletedAt;
        }

        /**
         * \u8bbe\u7f6e\u8bb0\u5f55\u88ab\u903b\u8f91\u5220\u9664\u7684\u65f6\u95f4\u3002
         * @param deletedAt \u8981\u5199\u5165\u7684\u503c
         */
        public void setDeletedAt(LocalDateTime deletedAt) {
            this.deletedAt = deletedAt;
        }
    }

    /** \u6301\u4e45\u5316\u5c42\u4f7f\u7528\u7684\u8ba2\u5355\u6d4b\u8bd5\u6570\u636e\u6a21\u578b\u3002 */
    @TableName("orders")
    static final class OrderPO {

        /** \u7528\u4e8e\u6784\u9020\u548c\u5b9a\u4f4d\u6d4b\u8bd5\u8bb0\u5f55\u7684\u4e3b\u952e\u3002 */
        @TableId
        private String id;

        /** \u6d4b\u8bd5\u5bf9\u8c61\u5f53\u524d\u7684\u4e1a\u52a1\u72b6\u6001\u3002 */
        @TableField("order_status")
        private String status;

        /** \u8bb0\u5f55\u88ab\u903b\u8f91\u5220\u9664\u7684\u65f6\u95f4\u3002 */
        @TableField("deleted_at")
        private LocalDateTime deletedAt;

        /** \u521b\u5efa\u5b57\u6bb5\u4f7f\u7528\u9ed8\u8ba4\u521d\u59cb\u503c\u7684 <code>OrderPO</code> \u6d4b\u8bd5\u5bf9\u8c61\u3002 */
        OrderPO() {
        }

        /**
         * \u7528\u7ed9\u5b9a\u503c\u521d\u59cb\u5316 <code>OrderPO</code> \u5b9e\u4f8b\u3002
         * @param id <code>id</code> \u5b57\u6bb5
         * @param status <code>status</code> \u5b57\u6bb5
         * @param deletedAt \u8bb0\u5f55\u88ab\u903b\u8f91\u5220\u9664\u7684\u65f6\u95f4
         */
        OrderPO(String id, String status, LocalDateTime deletedAt) {
            this.id = id;
            this.status = status;
            this.deletedAt = deletedAt;
        }

        /**
         * \u8bfb\u53d6<code>id</code> \u5b57\u6bb5\u3002
         * @return <code>id</code> \u5b57\u6bb5
         */
        public String getId() {
            return id;
        }

        /**
         * \u8bbe\u7f6e<code>id</code> \u5b57\u6bb5\u3002
         * @param id \u8981\u5199\u5165\u7684\u503c
         */
        public void setId(String id) {
            this.id = id;
        }

        /**
         * \u8bfb\u53d6<code>status</code> \u5b57\u6bb5\u3002
         * @return <code>status</code> \u5b57\u6bb5
         */
        public String getStatus() {
            return status;
        }

        /**
         * \u8bbe\u7f6e<code>status</code> \u5b57\u6bb5\u3002
         * @param status \u8981\u5199\u5165\u7684\u503c
         */
        public void setStatus(String status) {
            this.status = status;
        }

        /**
         * \u8bfb\u53d6\u8bb0\u5f55\u88ab\u903b\u8f91\u5220\u9664\u7684\u65f6\u95f4\u3002
         * @return \u8bb0\u5f55\u88ab\u903b\u8f91\u5220\u9664\u7684\u65f6\u95f4
         */
        public LocalDateTime getDeletedAt() {
            return deletedAt;
        }

        /**
         * \u8bbe\u7f6e\u8bb0\u5f55\u88ab\u903b\u8f91\u5220\u9664\u7684\u65f6\u95f4\u3002
         * @param deletedAt \u8981\u5199\u5165\u7684\u503c
         */
        public void setDeletedAt(LocalDateTime deletedAt) {
            this.deletedAt = deletedAt;
        }
    }
}
