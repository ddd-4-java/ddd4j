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
package io.ddd4j.data.event.store.r2dbc;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.ddd4j.core.cqrs.eventstore.AggregateVersionConflictException;
import io.ddd4j.core.cqrs.eventstore.StoredEvent;
import io.ddd4j.core.ddd.event.*;
import io.r2dbc.postgresql.PostgresqlConnectionConfiguration;
import io.r2dbc.postgresql.PostgresqlConnectionFactory;
import io.r2dbc.spi.Connection;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import reactor.core.publisher.Mono;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * PostgreSQL 容器轨：验证 R2DBC EventStore 自身 DDL 与事件往返。
 */
@Testcontainers(disabledWithoutDocker = true)
class R2dbcEventStorePostgresIT {

    /**
     * 为当前集成测试提供独立 PostgreSQL 服务的容器。
     */
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withTmpFs(Map.of("/var/lib/postgresql/data", "rw"));
    /**
     * 测试事件流中使用的聚合类型名称。
     */
    private static final String ORDER_TYPE = "Order";
    /**
     * 为受测 R2DBC 组件提供数据库连接的工厂。
     */
    private PostgresqlConnectionFactory connectionFactory;
    /**
     * 读取及追加领域事件的事件存储。
     */
    private R2dbcEventStore eventStore;

    /**
     * 测试前依次注册<code>EntityIdRegistry</code>。
     */
    @BeforeEach
    void setUp() {
        connectionFactory = new PostgresqlConnectionFactory(PostgresqlConnectionConfiguration.builder()
                .host(POSTGRES.getHost())
                .port(POSTGRES.getMappedPort(5432))
                .database(POSTGRES.getDatabaseName())
                .username(POSTGRES.getUsername())
                .password(POSTGRES.getPassword())
                .build());
        EntityIdRegistry.register("Order", TestAggregateRootId::new);
        eventStore = new R2dbcEventStore(connectionFactory);
    }

    /**
     * 测试后依次注销<code>EntityIdRegistry</code>、关闭<code>connection</code>。
     */
    @AfterEach
    void tearDown() {
        EntityIdRegistry.unregister("Order");
        Connection connection = Mono.from(connectionFactory.create()).block();
        if (connection != null) {
            Mono.from(connection.createStatement("DROP TABLE IF EXISTS DDD4J_EVENT_STORE").execute()).block();
            Mono.from(connection.close()).block();
        }
    }

    /**
     * 验证<code>events</code> 的大小为 <code>1</code>，<code>events.get(0).aggregateType()</code> 等于 <code>ORDER_TYPE</code>，<code>events.get(0).eventId()</code> 等于 <code>event.getEventId()</code>。
     */
    @Test
    void appendAndReadShouldRoundTripTypedMetadataOnPostgres() {
        TestAggregateRootId orderId = new TestAggregateRootId("order-pg-1");
        OrderCreatedEvent event = new OrderCreatedEvent(orderId);

        eventStore.append(ORDER_TYPE, orderId, List.of(event), 0L);

        List<StoredEvent> events = eventStore.read(ORDER_TYPE, orderId);
        assertThat(events).hasSize(1);
        assertThat(events.get(0).aggregateType()).isEqualTo(ORDER_TYPE);
        assertThat(events.get(0).aggregateId()).isEqualTo(orderId);
        assertThat(events.get(0).payload()).isInstanceOf(OrderCreatedEvent.class);
        assertThat(events.get(0).eventId()).isEqualTo(event.getEventId());
    }

    /**
     * 验证<code>eventStore.read("Invoice", orderId)</code> 的大小为 <code>1</code>。
     */
    @Test
    void versionRangeAndAggregateTypeIsolationShouldFollowCoreContractOnPostgres() {
        TestAggregateRootId orderId = new TestAggregateRootId("order-pg-shared");
        eventStore.append(ORDER_TYPE, orderId, Arrays.asList(
                new OrderCreatedEvent(orderId), new OrderCreatedEvent(orderId), new OrderCreatedEvent(orderId)), 0L);
        eventStore.append("Invoice", orderId, List.of(new OrderCreatedEvent(orderId)), 0L);

        assertThat(eventStore.read(ORDER_TYPE, orderId)).extracting(StoredEvent::version)
                .containsExactly(1L, 2L, 3L);
        assertThat(eventStore.read(ORDER_TYPE, orderId, 1, 2))
                .extracting(StoredEvent::version).containsExactly(1L, 2L);
        assertThat(eventStore.read("Invoice", orderId)).hasSize(1);
    }

    /**
     * 在测试框架中执行 <code>eventStore.append()</code>、<code>eventStore.readAll()</code>，验证该场景。
     */
    @Test
    void readAllShouldPreserveGlobalPositionOrderOnPostgres() {
        TestAggregateRootId first = new TestAggregateRootId("order-pg-a");
        TestAggregateRootId second = new TestAggregateRootId("order-pg-b");
        eventStore.append(ORDER_TYPE, first, List.of(new OrderCreatedEvent(first)), 0L);
        eventStore.append(ORDER_TYPE, second, List.of(new OrderCreatedEvent(second)), 0L);

        assertThat(eventStore.readAll(1, 10)).extracting(StoredEvent::position)
                .containsExactly(1L, 2L);
    }

    /**
     * 验证<code>eventStore.append(ORDER_TYPE, orderId, List.of(new OrderCreatedEvent(orderId)), 0L)</code> 抛出 <code>AggregateVersionConflictException.class</code>，<code>conflict.expectedVersion()</code> 等于 <code>0L</code>，<code>eventStore.read(ORDER_TYPE, orderId)</code> 的大小为 <code>1</code>。
     */
    @Test
    void appendWithStaleVersionShouldRejectOnPostgres() {
        TestAggregateRootId orderId = new TestAggregateRootId("order-pg-conflict");
        eventStore.append(ORDER_TYPE, orderId, List.of(new OrderCreatedEvent(orderId)), 0L);

        assertThatThrownBy(() -> eventStore.append(ORDER_TYPE, orderId,
                List.of(new OrderCreatedEvent(orderId)), 0L))
                .isInstanceOf(AggregateVersionConflictException.class)
                .satisfies(error -> {
                    AggregateVersionConflictException conflict = (AggregateVersionConflictException) error;
                    assertThat(conflict.expectedVersion()).isEqualTo(0L);
                    assertThat(conflict.actualVersion()).isEqualTo(1L);
                });
        assertThat(eventStore.read(ORDER_TYPE, orderId)).hasSize(1);
    }

    /**
     * 测试专用聚合根标识，供仓储、事件序列化或命令路由构造独立的聚合实例。
     */
    private final static class TestAggregateRootId implements AggregateRootId {

        /**
         * Java 序列化使用的版本标识。
         */
        private static final long serialVersionUID = 0L;
        /**
         * 测试聚合注册和反序列化所用的实体类型标识。
         */
        private static final EntityType TYPE = new StringEntityType("Order");
        /**
         * 用于组成测试标识的原始值。
         */
        private final String value;

        /**
         * 以给定状态构造 <code>TestAggregateRootId</code> 测试对象。
         *
         * @param value 用于组成测试标识的原始值
         */
        @JsonCreator()
        private TestAggregateRootId(@JsonProperty("value") String value) {
            this.value = value;
        }

        /**
         * 返回当前值或标识公开的类型。
         *
         * @return 实际返回<code>TYPE</code>，供调用方取得当前值
         */
        @Override
        public EntityType getType() {
            return TYPE;
        }

        /**
         * 返回聚合或实体标识的业务字符串。
         *
         * @return 实际返回<code>value</code>，供调用方取得当前值
         */
        @Override
        public String asString() {
            return value;
        }

        /**
         * 返回带类型前缀的标识字符串。
         *
         * <p>测试夹具会调用 <code>TYPE.asString()</code>。</p>
         *
         * @return 实际返回<code>TYPE.asString() + ":" + value</code>
         */
        @Override
        public String asTypedString() {
            return TYPE.asString() + ":" + value;
        }

        /**
         * 读取<code>value</code> 字段。
         *
         * @return <code>value</code> 字段
         */
        @JsonProperty("value")
        public String value() {
            return value;
        }

        /**
         * 根据<code>value</code>比较对象的值是否相等。
         *
         * <p>只比较运行时类型相同的对象。</p>
         *
         * @param obj 用来比较或转换的输入对象
         * @return 对象满足此实现的相等条件时为真
         */
        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (Objects.isNull(obj) || getClass() != obj.getClass()) {
                return false;
            }
            TestAggregateRootId other = (TestAggregateRootId) obj;
            return Objects.equals(this.value, other.value);
        }

        /**
         * 计算与对象相等条件一致的哈希值。
         *
         * @return 由参与相等比较的状态计算出的哈希值
         */
        @Override
        public int hashCode() {
            int result = 0;
            result = 31 * result + Objects.hashCode(value);
            return result;
        }

        /**
         * 生成用于调试和日志的对象描述，包含<code>value</code>。
         *
         * @return 表示当前对象状态的字符串
         */
        @Override
        public String toString() {
            return "TestAggregateRootId[value=" + value + "]";
        }
    }

    /**
     * 表示订单创建的测试领域事件，供断言验证事件处理链。
     */
    static final class OrderCreatedEvent extends DomainEvent<TestAggregateRootId> {

        /**
         * 使用 <code>super()</code> 初始化 <code>OrderCreatedEvent</code> 测试夹具。
         */
        OrderCreatedEvent() {
            super();
        }

        /**
         * 使用 <code>super()</code> 初始化 <code>OrderCreatedEvent</code> 测试夹具。
         *
         * @param orderId 测试中要定位的订单标识
         */
        OrderCreatedEvent(TestAggregateRootId orderId) {
            super(new EntityIdPath(orderId));
        }
    }
}
