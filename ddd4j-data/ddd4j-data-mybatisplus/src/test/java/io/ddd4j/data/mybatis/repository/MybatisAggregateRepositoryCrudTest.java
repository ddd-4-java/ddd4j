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

    private OrderMapper mapper;
    private TestRepository repository;

    @BeforeEach
    void setUp() {
        mapper = mock(OrderMapper.class);
        repository = new TestRepository(mapper);
    }

    // =================== findById ===================

    @Test
    void findById_shouldReturnModelWhenExists() {
        OrderPO po = new OrderPO("1", "PAID", null);
        when(mapper.selectById("1")).thenReturn(po);

        Optional<Order> result = repository.findById("1");

        assertThat(result).isPresent();
        assertThat(result.get().id()).isEqualTo("1");
        assertThat(result.get().getStatus()).isEqualTo("PAID");
    }

    @Test
    void findById_shouldReturnEmptyWhenNotExists() {
        when(mapper.selectById("999")).thenReturn(null);

        Optional<Order> result = repository.findById("999");

        assertThat(result).isEmpty();
    }

    @Test
    void findById_shouldReturnEmptyWhenIdIsNull() {
        Optional<Order> result = repository.findById(null);

        assertThat(result).isEmpty();
        verify(mapper, never()).selectById(any());
    }

    // =================== existsById ===================

    @Test
    void existsById_shouldReturnTrueWhenExists() {
        when(mapper.selectById("1")).thenReturn(new OrderPO("1", "PAID", null));

        boolean result = repository.existsById("1");

        assertThat(result).isTrue();
    }

    @Test
    void existsById_shouldReturnFalseWhenNotExists() {
        when(mapper.selectById("999")).thenReturn(null);

        boolean result = repository.existsById("999");

        assertThat(result).isFalse();
    }

    @Test
    void existsById_shouldReturnFalseWhenIdIsNull() {
        boolean result = repository.existsById(null);

        assertThat(result).isFalse();
    }

    // =================== save ===================

    @Test
    void save_shouldInsertWhenNew() {
        Order order = new Order(null, "DRAFT", null);
        when(mapper.selectById(any(Serializable.class))).thenReturn(null);
        when(mapper.insert(any(OrderPO.class))).thenReturn(1);

        Order saved = repository.save(order);

        verify(mapper).insert(any(OrderPO.class));
        verify(mapper, never()).updateById(any(OrderPO.class));
    }

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

    @Test
    void deleteById_shouldCallMapper() {
        repository.deleteById("1");

        verify(mapper).deleteById(eq("1"));
    }

    @Test
    void deleteById_shouldNotCallMapperWhenIdIsNull() {
        repository.deleteById(null);

        verify(mapper, never()).deleteById(any(Serializable.class));
    }

    // =================== count ===================

    @Test
    void count_shouldReturnCount() {
        when(mapper.selectCount(any(QueryWrapper.class))).thenReturn(5L);

        long result = repository.count();

        assertThat(result).isEqualTo(5L);
    }

    @Test
    void count_shouldReturnZeroWhenNull() {
        when(mapper.selectCount(any(QueryWrapper.class))).thenReturn(null);

        long result = repository.count();

        assertThat(result).isEqualTo(0L);
    }

    // =================== findAll ===================

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

    @Test
    void findAll_shouldReturnEmptyListWhenNoRecords() {
        when(mapper.selectList(any(QueryWrapper.class))).thenReturn(Collections.emptyList());

        List<Order> result = repository.findAll();

        assertThat(result).isEmpty();
    }

    // =================== findFirst ===================

    @Test
    void findFirst_shouldReturnFirstModel() {
        OrderPO po = new OrderPO("1", "PAID", null);
        when(mapper.selectList(any(QueryWrapper.class))).thenReturn(List.of(po));

        Optional<Order> result = repository.findFirst();

        assertThat(result).isPresent();
        assertThat(result.get().id()).isEqualTo("1");
    }

    @Test
    void findFirst_shouldReturnEmptyWhenNoRecords() {
        when(mapper.selectList(any(QueryWrapper.class))).thenReturn(Collections.emptyList());

        Optional<Order> result = repository.findFirst();

        assertThat(result).isEmpty();
    }

    // =================== toModel / toPersistenceObject ===================

    @Test
    void toModel_shouldConvertPoToModel() {
        OrderPO po = new OrderPO("1", "PAID", LocalDateTime.of(2026, 1, 1, 0, 0));

        Order model = repository.toModel(po);

        assertThat(model).isNotNull();
        assertThat(model.id()).isEqualTo("1");
        assertThat(model.getStatus()).isEqualTo("PAID");
    }

    @Test
    void toModel_shouldReturnNullWhenPoIsNull() {
        Order model = repository.toModel(null);

        assertThat(model).isNull();
    }

    @Test
    void toPersistenceObject_shouldConvertModelToPo() {
        Order model = new Order("1", "PAID", null);

        OrderPO po = repository.toPersistenceObject(model);

        assertThat(po).isNotNull();
        assertThat(po.getId()).isEqualTo("1");
        assertThat(po.getStatus()).isEqualTo("PAID");
    }

    @Test
    void toPersistenceObject_shouldReturnNullWhenModelIsNull() {
        OrderPO po = repository.toPersistenceObject(null);

        assertThat(po).isNull();
    }

    // =================== 辅助类 ===================

    static final class TestRepository
            extends MybatisAggregateRepository<OrderMapper, Order, OrderPO, OrderQuery, String> {

        TestRepository(OrderMapper mapper) {
            super(mapper, Order.class, OrderPO.class);
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

        Order() {
        }

        Order(String id, String status, LocalDateTime deletedAt) {
            this.id = id;
            this.status = status;
            this.deletedAt = deletedAt;
        }

        @Override
        public String id() {
            return id;
        }

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }

        LocalDateTime getDeletedAt() {
            return deletedAt;
        }

        public void setDeletedAt(LocalDateTime deletedAt) {
            this.deletedAt = deletedAt;
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

        OrderPO() {
        }

        OrderPO(String id, String status, LocalDateTime deletedAt) {
            this.id = id;
            this.status = status;
            this.deletedAt = deletedAt;
        }

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }

        public LocalDateTime getDeletedAt() {
            return deletedAt;
        }

        public void setDeletedAt(LocalDateTime deletedAt) {
            this.deletedAt = deletedAt;
        }
    }
}
