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

    /**
     * 验证<code>wrapper.getCustomSqlSegment()</code> 包含 <code>"order_status"</code>、<code>"deleted_at"</code>、<code>"IS NULL"</code>。
     */
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

    /**
     * 在测试框架中执行 <code>mock()</code>、<code>query.withPO()</code>、<code>repository.wrapper()</code>，验证该场景。
     */
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

    /**
     * 验证<code>repository.wrapper(query)</code> 抛出 <code>IllegalArgumentException.class</code>。
     */
    @Test
    void persistenceScopeShouldRejectPoFromAnotherRepository() {
        TestRepository repository = new TestRepository(mock(OrderMapper.class));
        OrderQuery query = new OrderQuery();
        query.withPO(OtherOrderPO.class).eq(OtherOrderPO::getStatus, "PAID");

        assertThatThrownBy(() -> repository.wrapper(query))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("PERSISTENCE", OtherOrderPO.class.getName(), OrderPO.class.getName());
    }

    /**
     * 为订单持久化测试提供的映射入口。
     */
    interface OrderMapper extends BaseMapper<OrderPO> {
    }

    /**
     * 实现 <code>MybatisAggregateRepository</code>，供测试调用 <code>wrapper()</code> 并观察结果。
     */
    static final class TestRepository
            extends MybatisAggregateRepository<OrderMapper, Order, OrderPO, OrderQuery, String> {

        /**
         * 使用 <code>super()</code> 初始化 <code>TestRepository</code> 测试夹具。
         *
         * @param mapper 当前测试登记或使用的持久化映射组件
         */
        TestRepository(OrderMapper mapper) {
            super(mapper, Order.class, OrderPO.class);
        }

        /**
         * 通过 <code>getBaseWrapper()</code> 取得当前测试需要的结果。
         *
         * @param query 用来读取测试数据的查询对象
         * @return 当前测试使用的结果，取自 <code>getBaseWrapper(query)</code>
         */
        QueryWrapper<OrderPO> wrapper(Query<Order> query) {
            return getBaseWrapper(query);
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
        String getStatus() {
            return status;
        }

        /**
         * 读取记录被逻辑删除的时间。
         *
         * @return 记录被逻辑删除的时间
         */
        LocalDateTime getDeletedAt() {
            return deletedAt;
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
         * 读取<code>status</code> 字段。
         *
         * @return <code>status</code> 字段
         */
        String getStatus() {
            return status;
        }

        /**
         * 读取记录被逻辑删除的时间。
         *
         * @return 记录被逻辑删除的时间
         */
        LocalDateTime getDeletedAt() {
            return deletedAt;
        }
    }

    /**
     * 将 <code>status</code> 等状态一起传入当前测试，供调用或结果断言使用。
     */
    static final class OtherOrderPO {

        /**
         * 测试对象当前的业务状态。
         */
        private String status;

        /**
         * 读取<code>status</code> 字段。
         *
         * @return <code>status</code> 字段
         */
        String getStatus() {
            return status;
        }
    }
}
