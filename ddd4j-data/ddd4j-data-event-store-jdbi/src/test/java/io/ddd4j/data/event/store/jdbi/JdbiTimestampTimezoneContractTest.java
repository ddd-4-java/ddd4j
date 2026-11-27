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
package io.ddd4j.data.event.store.jdbi;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.ddd4j.core.constant.EventStoreConstants;
import io.ddd4j.core.cqrs.eventstore.EventStore;
import io.ddd4j.core.ddd.event.*;
import org.jdbi.v3.core.Jdbi;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.TimeZone;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 检查事件存储时间戳在不同进程默认时区下保持同一 UTC 瞬间。
 */
class JdbiTimestampTimezoneContractTest {

    /**
     * 核对<code>stored</code> 等于 <code>LocalDateTime.ofInstant(expected, ZoneOffset.UTC)</code>，<code>store.read("Order", id).get(0).timestamp().toInstant()</code> 等于 <code>expected</code>。
     *
     * @param jdbi 传给 <code>jdbi.withHandle()</code> 的 <code>Jdbi</code> 输入
     * @throws Exception 反射访问被测成员时失败
     */
    static void assertUtcRoundTrip(Jdbi jdbi) throws Exception {
        TimeZone original = TimeZone.getDefault();
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("Asia/Shanghai"));
            EventStore store = new JdbiEventStore(jdbi);
            TestAggregateRootId id = new TestAggregateRootId("order-timezone");
            TimeEvent event = new TimeEvent(id);
            Instant expected = Instant.parse("2024-01-02T03:04:05Z");
            Field timestamp = DomainEvent.class.getDeclaredField("eventTimestamp");
            timestamp.setAccessible(true);
            timestamp.set(event, ZonedDateTime.ofInstant(expected, ZoneOffset.UTC));

            store.append("Order", id, Collections.<DomainEvent<?>>singletonList(event), 0);
            TimeZone.setDefault(TimeZone.getTimeZone("UTC"));

            LocalDateTime stored = jdbi.withHandle(handle -> handle.createQuery(
                            "SELECT " + EventStoreConstants.COLUMN_TIMESTAMP + " FROM "
                                    + EventStoreConstants.TABLE_NAME + " WHERE "
                                    + EventStoreConstants.COLUMN_AGGREGATE_ID + " = :id")
                    .bind("id", id.asString())
                    .map((row, context) -> row.getObject(1, LocalDateTime.class))
                    .one());
            assertEquals(LocalDateTime.ofInstant(expected, ZoneOffset.UTC), stored);
            assertEquals(expected, store.read("Order", id).get(0).timestamp().toInstant());
        } finally {
            TimeZone.setDefault(original);
        }
    }

    /**
     * 在测试框架中执行 <code>UUID.randomUUID()</code>、<code>Jdbi.create()</code>，验证该场景。
     *
     * @throws Exception 当前用例准备资源或调用测试入口失败
     */
    @Test
    void persistedTimestampIsUtcIndependentOfWriterAndReaderTimezones() throws Exception {
        String database = "jdbi_timezone_" + UUID.randomUUID().toString().replace("-", "");
        Jdbi jdbi = Jdbi.create("jdbc:h2:mem:" + database + ";DB_CLOSE_DELAY=-1", "sa", "");
        assertUtcRoundTrip(jdbi);
    }

    /**
     * 测试专用聚合根标识，供仓储、事件序列化或命令路由构造独立的聚合实例。
     */
    private static final class TestAggregateRootId implements AggregateRootId {

        /**
         * Java 序列化使用的版本标识。
         */
        private static final long serialVersionUID = 1L;
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
        @JsonCreator
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
    }

    /**
     * 测试事件载荷。
     */
    public static final class TimeEvent extends DomainEvent<TestAggregateRootId> {

        /**
         * 使用 <code>super()</code> 初始化 <code>TimeEvent</code> 测试夹具。
         */
        public TimeEvent() {
            super();
        }

        /**
         * 使用 <code>super()</code> 初始化 <code>TimeEvent</code> 测试夹具。
         *
         * @param id 当前要查找的测试记录标识
         */
        private TimeEvent(TestAggregateRootId id) {
            super(new EntityIdPath(id));
        }
    }
}
