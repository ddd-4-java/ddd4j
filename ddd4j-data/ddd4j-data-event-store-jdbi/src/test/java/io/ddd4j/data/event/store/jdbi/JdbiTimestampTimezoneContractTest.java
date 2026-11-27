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
import io.ddd4j.core.ddd.event.AggregateRootId;
import io.ddd4j.core.ddd.event.DomainEvent;
import io.ddd4j.core.ddd.event.EntityIdPath;
import io.ddd4j.core.ddd.event.EntityType;
import io.ddd4j.core.ddd.event.StringEntityType;
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

/** 检查事件存储时间戳在不同进程默认时区下保持同一 UTC 瞬间。 */
class JdbiTimestampTimezoneContractTest {

    /**
     * \u5728\u6d4b\u8bd5\u6846\u67b6\u4e2d\u6267\u884c <code>UUID.randomUUID()</code>\u3001<code>Jdbi.create()</code>\uff0c\u9a8c\u8bc1\u8be5\u573a\u666f\u3002
     * @throws Exception \u5f53\u524d\u7528\u4f8b\u51c6\u5907\u8d44\u6e90\u6216\u8c03\u7528\u6d4b\u8bd5\u5165\u53e3\u5931\u8d25
     */
    @Test
    void persistedTimestampIsUtcIndependentOfWriterAndReaderTimezones() throws Exception {
        String database = "jdbi_timezone_" + UUID.randomUUID().toString().replace("-", "");
        Jdbi jdbi = Jdbi.create("jdbc:h2:mem:" + database + ";DB_CLOSE_DELAY=-1", "sa", "");
        assertUtcRoundTrip(jdbi);
    }

    /**
     * \u6838\u5bf9<code>stored</code> \u7b49\u4e8e <code>LocalDateTime.ofInstant(expected, ZoneOffset.UTC)</code>\uff0c<code>store.read("Order", id).get(0).timestamp().toInstant()</code> \u7b49\u4e8e <code>expected</code>\u3002
     * @param jdbi \u4f20\u7ed9 <code>jdbi.withHandle()</code> \u7684 <code>Jdbi</code> \u8f93\u5165
     * @throws Exception \u53cd\u5c04\u8bbf\u95ee\u88ab\u6d4b\u6210\u5458\u65f6\u5931\u8d25
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

    /** \u6d4b\u8bd5\u4e13\u7528\u805a\u5408\u6839\u6807\u8bc6\uff0c\u4f9b\u4ed3\u50a8\u3001\u4e8b\u4ef6\u5e8f\u5217\u5316\u6216\u547d\u4ee4\u8def\u7531\u6784\u9020\u72ec\u7acb\u7684\u805a\u5408\u5b9e\u4f8b\u3002 */
    private static final class TestAggregateRootId implements AggregateRootId {

        /** Java \u5e8f\u5217\u5316\u4f7f\u7528\u7684\u7248\u672c\u6807\u8bc6\u3002 */
        private static final long serialVersionUID = 1L;
        /** \u6d4b\u8bd5\u805a\u5408\u6ce8\u518c\u548c\u53cd\u5e8f\u5217\u5316\u6240\u7528\u7684\u5b9e\u4f53\u7c7b\u578b\u6807\u8bc6\u3002 */
        private static final EntityType TYPE = new StringEntityType("Order");
        /** \u7528\u4e8e\u7ec4\u6210\u6d4b\u8bd5\u6807\u8bc6\u7684\u539f\u59cb\u503c\u3002 */
        private final String value;

        /**
         * \u4ee5\u7ed9\u5b9a\u72b6\u6001\u6784\u9020 <code>TestAggregateRootId</code> \u6d4b\u8bd5\u5bf9\u8c61\u3002
         * @param value \u7528\u4e8e\u7ec4\u6210\u6d4b\u8bd5\u6807\u8bc6\u7684\u539f\u59cb\u503c
         */
        @JsonCreator
        private TestAggregateRootId(@JsonProperty("value") String value) {
            this.value = value;
        }

        /** {@inheritDoc} */
        @Override
        public EntityType getType() {
            return TYPE;
        }

        /** {@inheritDoc} */
        @Override
        public String asString() {
            return value;
        }

        /** {@inheritDoc} */
        @Override
        public String asTypedString() {
            return TYPE.asString() + ":" + value;
        }

        /**
         * \u8bfb\u53d6<code>value</code> \u5b57\u6bb5\u3002
         * @return <code>value</code> \u5b57\u6bb5
         */
        @JsonProperty("value")
        public String value() {
            return value;
        }
    }

    /** 测试事件载荷。 */
    public static final class TimeEvent extends DomainEvent<TestAggregateRootId> {

        /** \u4f7f\u7528 <code>super()</code> \u521d\u59cb\u5316 <code>TimeEvent</code> \u6d4b\u8bd5\u5939\u5177\u3002 */
        public TimeEvent() {
            super();
        }

        /**
         * \u4f7f\u7528 <code>super()</code> \u521d\u59cb\u5316 <code>TimeEvent</code> \u6d4b\u8bd5\u5939\u5177\u3002
         * @param id \u5f53\u524d\u8981\u67e5\u627e\u7684\u6d4b\u8bd5\u8bb0\u5f55\u6807\u8bc6
         */
        private TimeEvent(TestAggregateRootId id) {
            super(new EntityIdPath(id));
        }
    }
}
