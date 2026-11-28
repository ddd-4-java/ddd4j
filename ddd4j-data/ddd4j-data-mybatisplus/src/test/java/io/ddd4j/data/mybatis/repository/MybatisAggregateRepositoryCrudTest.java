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
package io.ddd4j.data.mybatis.repository;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.ddd4j.core.cqrs.query.Query;
import io.ddd4j.core.ddd.model.AggregateRoot;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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

    /**
     * 当前被验证或替换的持久化 Mapper 实例。
     */
    private OrderMapper mapper;
    /**
     * 被测仓储或用于提供测试数据的仓储实例。
     */
    private TestRepository repository;

    /**
     * 测试前依次初始化<code>repository</code>。
     */
    @BeforeEach
    void setUp() {
        mapper = mock(OrderMapper.class);
        repository = new TestRepository(mapper);
    }

    // =================== findById ===================

    /** 单个聚合交给扩展点后可补充字段，同时禁止替换、添加和删除列表元素。 */
    @Test
    void singleModelFillAllowsFieldChangesButRejectsListChanges() {
        Order order = new Order("1", "DRAFT", null);
        TestRepository fillingRepository = new TestRepository(mapper) {
            @Override
            public void fill(Query<Order> query, List<Order> models) {
                assertThat(models).containsExactly(order);
                assertThatThrownBy(() -> models.set(0, order)).isInstanceOf(UnsupportedOperationException.class);
                assertThatThrownBy(() -> models.add(order)).isInstanceOf(UnsupportedOperationException.class);
                assertThatThrownBy(() -> models.remove(0)).isInstanceOf(UnsupportedOperationException.class);
                models.get(0).setStatus("PAID");
            }
        };

        fillingRepository.fill(new OrderQuery(), order);
        assertThat(order.getStatus()).isEqualTo("PAID");
    }

    /**
     * 验证<code>result</code>存在值，<code>result.get().id()</code> 等于 <code>"1"</code>，<code>result.get().getStatus()</code> 等于 <code>"PAID"</code>。
     */
    @Test
    void findById_shouldReturnModelWhenExists() {
        OrderPO po = new OrderPO("1", "PAID", null);
        when(mapper.selectById("1")).thenReturn(po);

        Optional<Order> result = repository.findById("1");

        assertThat(result).isPresent();
        assertThat(result.get().id()).isEqualTo("1");
        assertThat(result.get().getStatus()).isEqualTo("PAID");
    }

    /**
     * 验证<code>result</code>为空。
     */
    @Test
    void findById_shouldReturnEmptyWhenNotExists() {
        when(mapper.selectById("999")).thenReturn(null);

        Optional<Order> result = repository.findById("999");

        assertThat(result).isEmpty();
    }

    /**
     * 验证<code>result</code>为空。
     */
    @Test
    void findById_shouldReturnEmptyWhenIdIsNull() {
        Optional<Order> result = repository.findById(null);

        assertThat(result).isEmpty();
        verify(mapper, never()).selectById(any());
    }

    // =================== existsById ===================

    /**
     * 验证<code>result</code>为真。
     */
    @Test
    void existsById_shouldReturnTrueWhenExists() {
        when(mapper.selectById("1")).thenReturn(new OrderPO("1", "PAID", null));

        boolean result = repository.existsById("1");

        assertThat(result).isTrue();
    }

    /**
     * 验证<code>result</code>为假。
     */
    @Test
    void existsById_shouldReturnFalseWhenNotExists() {
        when(mapper.selectById("999")).thenReturn(null);

        boolean result = repository.existsById("999");

        assertThat(result).isFalse();
    }

    /**
     * 验证<code>result</code>为假。
     */
    @Test
    void existsById_shouldReturnFalseWhenIdIsNull() {
        boolean result = repository.existsById(null);

        assertThat(result).isFalse();
    }

    // =================== save ===================

    /**
     * 验证调用了 <code>mapper.insert(any(OrderPO.class))</code>，没有调用 <code>mapper.updateById(any(OrderPO.class))</code>。
     */
    @Test
    void save_shouldInsertWhenNew() {
        Order order = new Order(null, "DRAFT", null);
        when(mapper.selectById(any(Serializable.class))).thenReturn(null);
        when(mapper.insert(any(OrderPO.class))).thenReturn(1);

        Order saved = repository.save(order);

        verify(mapper).insert(any(OrderPO.class));
        verify(mapper, never()).updateById(any(OrderPO.class));
    }

    /**
     * 验证调用了 <code>mapper.updateById(any(OrderPO.class))</code>，没有调用 <code>mapper.insert(any(OrderPO.class))</code>。
     */
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

    /**
     * 验证调用了 <code>mapper.deleteById(eq("1"))</code>。
     */
    @Test
    void deleteById_shouldCallMapper() {
        repository.deleteById("1");

        verify(mapper).deleteById(eq("1"));
    }

    /**
     * 验证没有调用 <code>mapper.deleteById(any(Serializable.class))</code>。
     */
    @Test
    void deleteById_shouldNotCallMapperWhenIdIsNull() {
        repository.deleteById(null);

        verify(mapper, never()).deleteById(any(Serializable.class));
    }

    // =================== count ===================

    /**
     * 验证<code>result</code> 等于 <code>5L</code>。
     */
    @Test
    void count_shouldReturnCount() {
        when(mapper.selectCount(any(QueryWrapper.class))).thenReturn(5L);

        long result = repository.count();

        assertThat(result).isEqualTo(5L);
    }

    /**
     * 验证<code>result</code> 等于 <code>0L</code>。
     */
    @Test
    void count_shouldReturnZeroWhenNull() {
        when(mapper.selectCount(any(QueryWrapper.class))).thenReturn(null);

        long result = repository.count();

        assertThat(result).isEqualTo(0L);
    }

    // =================== findAll ===================

    /**
     * 验证<code>result</code> 的大小为 <code>2</code>，<code>result.get(0).id()</code> 等于 <code>"1"</code>，<code>result.get(1).id()</code> 等于 <code>"2"</code>。
     */
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

    /**
     * 验证<code>result</code>为空。
     */
    @Test
    void findAll_shouldReturnEmptyListWhenNoRecords() {
        when(mapper.selectList(any(QueryWrapper.class))).thenReturn(Collections.emptyList());

        List<Order> result = repository.findAll();

        assertThat(result).isEmpty();
    }

    // =================== findFirst ===================

    /**
     * 验证<code>result</code>存在值，<code>result.get().id()</code> 等于 <code>"1"</code>。
     */
    @Test
    void findFirst_shouldReturnFirstModel() {
        OrderPO po = new OrderPO("1", "PAID", null);
        when(mapper.selectList(any(QueryWrapper.class))).thenReturn(List.of(po));

        Optional<Order> result = repository.findFirst();

        assertThat(result).isPresent();
        assertThat(result.get().id()).isEqualTo("1");
    }

    /**
     * 验证<code>result</code>为空。
     */
    @Test
    void findFirst_shouldReturnEmptyWhenNoRecords() {
        when(mapper.selectList(any(QueryWrapper.class))).thenReturn(Collections.emptyList());

        Optional<Order> result = repository.findFirst();

        assertThat(result).isEmpty();
    }

    // =================== toModel / toPersistenceObject ===================

    /**
     * 验证<code>model</code>非空，<code>model.id()</code> 等于 <code>"1"</code>，<code>model.getStatus()</code> 等于 <code>"PAID"</code>。
     */
    @Test
    void toModel_shouldConvertPoToModel() {
        OrderPO po = new OrderPO("1", "PAID", LocalDateTime.of(2026, 1, 1, 0, 0));

        Order model = repository.toModel(po);

        assertThat(model).isNotNull();
        assertThat(model.id()).isEqualTo("1");
        assertThat(model.getStatus()).isEqualTo("PAID");
    }

    /**
     * 验证<code>model</code>为空。
     */
    @Test
    void toModel_shouldReturnNullWhenPoIsNull() {
        Order model = repository.toModel(null);

        assertThat(model).isNull();
    }

    /**
     * 验证<code>po</code>非空，<code>po.getId()</code> 等于 <code>"1"</code>，<code>po.getStatus()</code> 等于 <code>"PAID"</code>。
     */
    @Test
    void toPersistenceObject_shouldConvertModelToPo() {
        Order model = new Order("1", "PAID", null);

        OrderPO po = repository.toPersistenceObject(model);

        assertThat(po).isNotNull();
        assertThat(po.getId()).isEqualTo("1");
        assertThat(po.getStatus()).isEqualTo("PAID");
    }

    /**
     * 验证<code>po</code>为空。
     */
    @Test
    void toPersistenceObject_shouldReturnNullWhenModelIsNull() {
        OrderPO po = repository.toPersistenceObject(null);

        assertThat(po).isNull();
    }

    // =================== 辅助类 ===================

    /**
     * 为订单持久化测试提供的映射入口。
     */
    interface OrderMapper extends BaseMapper<OrderPO> {
    }

    /**
     * 供测试验证泛型解析和仓储操作的具体仓储实现。
     */
    static class TestRepository
            extends MybatisAggregateRepository<OrderMapper, Order, OrderPO, OrderQuery, String> {

        /**
         * 使用 <code>super()</code> 初始化 <code>TestRepository</code> 测试夹具。
         *
         * @param mapper 当前测试登记或使用的持久化映射组件
         */
        TestRepository(OrderMapper mapper) {
            super(mapper, Order.class, OrderPO.class);
        }
    }

    /**
     * 订单测试查询对象，供构造仓储检索条件。
     */
    static final class OrderQuery extends Query<Order> {
    }

    /**
     * 供测试验证事件应用和持久化的聚合样本。
     */
    static final class Order extends AggregateRoot<String> {

        /**
         * 用于构造和定位测试记录的主键。
         */
        private String id;
        /**
         * 测试对象当前的业务状态。
         */
        private String status;
        /**
         * 记录被逻辑删除的时间。
         */
        private LocalDateTime deletedAt;

        /**
         * 创建字段使用默认初始值的 <code>Order</code> 测试对象。
         */
        Order() {
        }

        /**
         * 用给定值初始化 <code>Order</code> 实例。
         *
         * @param id        <code>id</code> 字段
         * @param status    <code>status</code> 字段
         * @param deletedAt 记录被逻辑删除的时间
         */
        Order(String id, String status, LocalDateTime deletedAt) {
            this.id = id;
            this.status = status;
            this.deletedAt = deletedAt;
        }

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
         * 读取<code>id</code> 字段。
         *
         * @return <code>id</code> 字段
         */
        public String getId() {
            return id;
        }

        /**
         * 设置<code>id</code> 字段。
         *
         * @param id 要写入的值
         */
        public void setId(String id) {
            this.id = id;
        }

        /**
         * 读取<code>status</code> 字段。
         *
         * @return <code>status</code> 字段
         */
        String getStatus() {
            return status;
        }

        /**
         * 设置<code>status</code> 字段。
         *
         * @param status 要写入的值
         */
        public void setStatus(String status) {
            this.status = status;
        }

        /**
         * 读取记录被逻辑删除的时间。
         *
         * @return 记录被逻辑删除的时间
         */
        LocalDateTime getDeletedAt() {
            return deletedAt;
        }

        /**
         * 设置记录被逻辑删除的时间。
         *
         * @param deletedAt 要写入的值
         */
        public void setDeletedAt(LocalDateTime deletedAt) {
            this.deletedAt = deletedAt;
        }
    }

    /**
     * 持久化层使用的订单测试数据模型。
     */
    @TableName("orders")
    static final class OrderPO {

        /**
         * 用于构造和定位测试记录的主键。
         */
        @TableId
        private String id;

        /**
         * 测试对象当前的业务状态。
         */
        @TableField("order_status")
        private String status;

        /**
         * 记录被逻辑删除的时间。
         */
        @TableField("deleted_at")
        private LocalDateTime deletedAt;

        /**
         * 创建字段使用默认初始值的 <code>OrderPO</code> 测试对象。
         */
        OrderPO() {
        }

        /**
         * 用给定值初始化 <code>OrderPO</code> 实例。
         *
         * @param id        <code>id</code> 字段
         * @param status    <code>status</code> 字段
         * @param deletedAt 记录被逻辑删除的时间
         */
        OrderPO(String id, String status, LocalDateTime deletedAt) {
            this.id = id;
            this.status = status;
            this.deletedAt = deletedAt;
        }

        /**
         * 读取<code>id</code> 字段。
         *
         * @return <code>id</code> 字段
         */
        public String getId() {
            return id;
        }

        /**
         * 设置<code>id</code> 字段。
         *
         * @param id 要写入的值
         */
        public void setId(String id) {
            this.id = id;
        }

        /**
         * 读取<code>status</code> 字段。
         *
         * @return <code>status</code> 字段
         */
        public String getStatus() {
            return status;
        }

        /**
         * 设置<code>status</code> 字段。
         *
         * @param status 要写入的值
         */
        public void setStatus(String status) {
            this.status = status;
        }

        /**
         * 读取记录被逻辑删除的时间。
         *
         * @return 记录被逻辑删除的时间
         */
        public LocalDateTime getDeletedAt() {
            return deletedAt;
        }

        /**
         * 设置记录被逻辑删除的时间。
         *
         * @param deletedAt 要写入的值
         */
        public void setDeletedAt(LocalDateTime deletedAt) {
            this.deletedAt = deletedAt;
        }
    }
}
