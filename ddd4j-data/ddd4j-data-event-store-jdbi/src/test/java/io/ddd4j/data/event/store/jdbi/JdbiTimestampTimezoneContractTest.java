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

    @Test
    void persistedTimestampIsUtcIndependentOfWriterAndReaderTimezones() throws Exception {
        String database = "jdbi_timezone_" + UUID.randomUUID().toString().replace("-", "");
        Jdbi jdbi = Jdbi.create("jdbc:h2:mem:" + database + ";DB_CLOSE_DELAY=-1", "sa", "");
        assertUtcRoundTrip(jdbi);
    }

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

    private static final class TestAggregateRootId implements AggregateRootId {

        private static final long serialVersionUID = 1L;
        private static final EntityType TYPE = new StringEntityType("Order");
        private final String value;

        @JsonCreator
        private TestAggregateRootId(@JsonProperty("value") String value) {
            this.value = value;
        }

        @Override
        public EntityType getType() {
            return TYPE;
        }

        @Override
        public String asString() {
            return value;
        }

        @Override
        public String asTypedString() {
            return TYPE.asString() + ":" + value;
        }

        @JsonProperty("value")
        public String value() {
            return value;
        }
    }

    /** 测试事件载荷。 */
    public static final class TimeEvent extends DomainEvent<TestAggregateRootId> {

        public TimeEvent() {
            super();
        }

        private TimeEvent(TestAggregateRootId id) {
            super(new EntityIdPath(id));
        }
    }
}
