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

    /** \u9a8c\u8bc1<code>wrapper.getCustomSqlSegment()</code> \u5305\u542b <code>"order_status"</code>\u3001<code>"deleted_at"</code>\u3001<code>"IS NULL"</code>\u3002 */
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

    /** \u5728\u6d4b\u8bd5\u6846\u67b6\u4e2d\u6267\u884c <code>mock()</code>\u3001<code>query.withPO()</code>\u3001<code>repository.wrapper()</code>\uff0c\u9a8c\u8bc1\u8be5\u573a\u666f\u3002 */
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

    /** \u9a8c\u8bc1<code>repository.wrapper(query)</code> \u629b\u51fa <code>IllegalArgumentException.class</code>\u3002 */
    @Test
    void persistenceScopeShouldRejectPoFromAnotherRepository() {
        TestRepository repository = new TestRepository(mock(OrderMapper.class));
        OrderQuery query = new OrderQuery();
        query.withPO(OtherOrderPO.class).eq(OtherOrderPO::getStatus, "PAID");

        assertThatThrownBy(() -> repository.wrapper(query))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("PERSISTENCE", OtherOrderPO.class.getName(), OrderPO.class.getName());
    }

    /** \u5b9e\u73b0 <code>MybatisAggregateRepository</code>\uff0c\u4f9b\u6d4b\u8bd5\u8c03\u7528 <code>wrapper()</code> \u5e76\u89c2\u5bdf\u7ed3\u679c\u3002 */
    static final class TestRepository
            extends MybatisAggregateRepository<OrderMapper, Order, OrderPO, OrderQuery, String> {

        /**
         * \u4f7f\u7528 <code>super()</code> \u521d\u59cb\u5316 <code>TestRepository</code> \u6d4b\u8bd5\u5939\u5177\u3002
         * @param mapper \u5f53\u524d\u6d4b\u8bd5\u767b\u8bb0\u6216\u4f7f\u7528\u7684\u6301\u4e45\u5316\u6620\u5c04\u7ec4\u4ef6
         */
        TestRepository(OrderMapper mapper) {
            super(mapper, Order.class, OrderPO.class);
        }

        /**
         * \u901a\u8fc7 <code>getBaseWrapper()</code> \u53d6\u5f97\u5f53\u524d\u6d4b\u8bd5\u9700\u8981\u7684\u7ed3\u679c\u3002
         * @param query \u7528\u6765\u8bfb\u53d6\u6d4b\u8bd5\u6570\u636e\u7684\u67e5\u8be2\u5bf9\u8c61
         * @return \u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u7ed3\u679c\uff0c\u53d6\u81ea <code>getBaseWrapper(query)</code>
         */
        QueryWrapper<OrderPO> wrapper(Query<Order> query) {
            return getBaseWrapper(query);
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
         * \u8bfb\u53d6\u8bb0\u5f55\u88ab\u903b\u8f91\u5220\u9664\u7684\u65f6\u95f4\u3002
         * @return \u8bb0\u5f55\u88ab\u903b\u8f91\u5220\u9664\u7684\u65f6\u95f4
         */
        LocalDateTime getDeletedAt() {
            return deletedAt;
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

        /**
         * \u8bfb\u53d6<code>status</code> \u5b57\u6bb5\u3002
         * @return <code>status</code> \u5b57\u6bb5
         */
        String getStatus() {
            return status;
        }

        /**
         * \u8bfb\u53d6\u8bb0\u5f55\u88ab\u903b\u8f91\u5220\u9664\u7684\u65f6\u95f4\u3002
         * @return \u8bb0\u5f55\u88ab\u903b\u8f91\u5220\u9664\u7684\u65f6\u95f4
         */
        LocalDateTime getDeletedAt() {
            return deletedAt;
        }
    }

    /** \u5c06 <code>status</code> \u7b49\u72b6\u6001\u4e00\u8d77\u4f20\u5165\u5f53\u524d\u6d4b\u8bd5\uff0c\u4f9b\u8c03\u7528\u6216\u7ed3\u679c\u65ad\u8a00\u4f7f\u7528\u3002 */
    static final class OtherOrderPO {

        /** \u6d4b\u8bd5\u5bf9\u8c61\u5f53\u524d\u7684\u4e1a\u52a1\u72b6\u6001\u3002 */
        private String status;

        /**
         * \u8bfb\u53d6<code>status</code> \u5b57\u6bb5\u3002
         * @return <code>status</code> \u5b57\u6bb5
         */
        String getStatus() {
            return status;
        }
    }
}
