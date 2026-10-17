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
package io.ddd4j.data.event.store.esdb;

import com.fasterxml.jackson.annotation.JsonProperty;

import com.fasterxml.jackson.annotation.JsonCreator;

import java.util.Objects;

import com.eventstore.dbclient.AppendToStreamOptions;
import com.eventstore.dbclient.EventData;
import com.eventstore.dbclient.EventStoreDBClient;
import com.eventstore.dbclient.ExpectedRevision;
import com.eventstore.dbclient.StreamNotFoundException;
import com.eventstore.dbclient.WrongExpectedVersionException;
import io.ddd4j.core.cqrs.eventstore.AggregateVersionConflictException;
import io.ddd4j.core.ddd.event.AggregateRootId;
import io.ddd4j.core.ddd.event.DomainEvent;
import io.ddd4j.core.ddd.event.EntityIdPath;
import io.ddd4j.core.ddd.event.EntityType;
import io.ddd4j.core.ddd.event.StringEntityType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * ESDB 强类型 EventStore 的单元契约。
 */
@ExtendWith(MockitoExtension.class)
class EsdbEventStoreTest {
    private static final String ORDER_TYPE = "Order";
    @Mock
    EventStoreDBClient client;
    @Captor
    ArgumentCaptor<String> streamCaptor;

    @Test
    void expectedRevisionShouldFollowCoreCurrentVersionContract() {
        assertThat(EsdbEventStore.toExpectedRevision(0)).isEqualTo(ExpectedRevision.noStream());
        assertThat(EsdbEventStore.toExpectedRevision(3)).isEqualTo(ExpectedRevision.expectedRevision(2));
    }

    @Test
    void appendShouldUseAggregateTypeInStreamAndPreserveCoreEventIdentity() {
        TestId id = new TestId("order-1");
        when(client.appendToStream(anyString(), any(AppendToStreamOptions.class), any(EventData[].class)))
                .thenReturn(CompletableFuture.completedFuture(null));

        new EsdbEventStore(client, "test-").append(ORDER_TYPE, id, List.of(new TestEvent(id)), 0);

        verify(client).appendToStream(streamCaptor.capture(), any(AppendToStreamOptions.class), any(EventData[].class));
        assertThat(streamCaptor.getValue()).isEqualTo("test-Order::order-1");
    }

    @Test
    void appendEmptyEventsMustNotCallClient() {
        new EsdbEventStore(client).append(ORDER_TYPE, new TestId("empty"), List.of(), 0);
        verifyNoInteractions(client);
    }

    @Test
    void versionConflictMustUseCoreException() {
        WrongExpectedVersionException conflict = org.mockito.Mockito.mock(WrongExpectedVersionException.class);
        when(conflict.getActualVersion()).thenReturn(ExpectedRevision.expectedRevision(0));
        when(client.appendToStream(anyString(), any(AppendToStreamOptions.class), any(EventData[].class)))
                .thenReturn(CompletableFuture.failedFuture(conflict));

        assertThatThrownBy(() -> new EsdbEventStore(client).append(ORDER_TYPE, new TestId("conflict"),
                List.of(new TestEvent(new TestId("conflict"))), 0))
                .isInstanceOf(AggregateVersionConflictException.class);
    }

    @Test
    void malformedMetadataMustFailBeforeDeserializingEvent() {
        com.eventstore.dbclient.ReadResult result = org.mockito.Mockito.mock(com.eventstore.dbclient.ReadResult.class);
        com.eventstore.dbclient.ResolvedEvent resolved = org.mockito.Mockito.mock(com.eventstore.dbclient.ResolvedEvent.class);
        com.eventstore.dbclient.RecordedEvent recorded = org.mockito.Mockito.mock(com.eventstore.dbclient.RecordedEvent.class);
        when(recorded.getUserMetadata()).thenReturn("{broken".getBytes(StandardCharsets.UTF_8));
        when(resolved.getEvent()).thenReturn(recorded);
        when(result.getEvents()).thenReturn(Collections.singletonList(resolved));
        when(client.readStream(anyString(), any())).thenReturn(CompletableFuture.completedFuture(result));

        assertThatThrownBy(() -> new EsdbEventStore(client).read(ORDER_TYPE, new TestId("corrupt")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Invalid ESDB event metadata");
    }

    @Test
    void nullMetadataMustFailBeforeDeserializingEvent() {
        com.eventstore.dbclient.ReadResult result = org.mockito.Mockito.mock(com.eventstore.dbclient.ReadResult.class);
        com.eventstore.dbclient.ResolvedEvent resolved = org.mockito.Mockito.mock(com.eventstore.dbclient.ResolvedEvent.class);
        com.eventstore.dbclient.RecordedEvent recorded = org.mockito.Mockito.mock(com.eventstore.dbclient.RecordedEvent.class);
        when(recorded.getUserMetadata()).thenReturn("null".getBytes(StandardCharsets.UTF_8));
        when(resolved.getEvent()).thenReturn(recorded);
        when(result.getEvents()).thenReturn(Collections.singletonList(resolved));
        when(client.readStream(anyString(), any())).thenReturn(CompletableFuture.completedFuture(result));

        assertThatThrownBy(() -> new EsdbEventStore(client).read(ORDER_TYPE, new TestId("null-metadata")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Invalid ESDB event metadata");
    }

    @Test
    void absentStronglyTypedStreamMustReadEmpty() {
        StreamNotFoundException absent = org.mockito.Mockito.mock(StreamNotFoundException.class);
        when(client.readStream(anyString(), any())).thenReturn(CompletableFuture.failedFuture(absent));
        assertThat(new EsdbEventStore(client).read(ORDER_TYPE, new TestId("missing"))).isEmpty();
    }

    @Test
    void existingEmptyStreamMustReturnUnmodifiableReadResults() {
        com.eventstore.dbclient.ReadResult result = org.mockito.Mockito.mock(com.eventstore.dbclient.ReadResult.class);
        when(result.getEvents()).thenReturn(Collections.emptyList());
        when(client.readStream(anyString(), any())).thenReturn(CompletableFuture.completedFuture(result));
        EsdbEventStore store = new EsdbEventStore(client);
        TestId id = new TestId("empty-stream");

        assertThatThrownBy(() -> store.read(ORDER_TYPE, id).add(null))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> store.read(ORDER_TYPE, id, 1, 2).add(null))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void readAllShouldExposeStableAggregateIdString() {
        com.eventstore.dbclient.ReadResult result = org.mockito.Mockito.mock(com.eventstore.dbclient.ReadResult.class);
        com.eventstore.dbclient.ResolvedEvent resolved = org.mockito.Mockito.mock(com.eventstore.dbclient.ResolvedEvent.class);
        com.eventstore.dbclient.RecordedEvent recorded = org.mockito.Mockito.mock(com.eventstore.dbclient.RecordedEvent.class);
        when(recorded.getStreamId()).thenReturn("Order::order-1");
        when(recorded.getPosition()).thenReturn(new com.eventstore.dbclient.Position(1, 1));
        when(recorded.getUserMetadata()).thenReturn(
                "{\"aggregateType\":\"Order\",\"aggregateId\":\"order-1\"}".getBytes(StandardCharsets.UTF_8));
        when(recorded.getEventData()).thenReturn("{}".getBytes(StandardCharsets.UTF_8));
        when(recorded.getEventType()).thenReturn(TestEvent.class.getName());
        when(recorded.getEventId()).thenReturn(java.util.UUID.randomUUID());
        when(recorded.getCreated()).thenReturn(java.time.Instant.parse("2026-01-01T00:00:00Z"));
        when(resolved.getEvent()).thenReturn(recorded);
        when(result.getEvents()).thenReturn(Collections.singletonList(resolved));
        when(client.readAll(any(com.eventstore.dbclient.ReadAllOptions.class)))
                .thenReturn(CompletableFuture.completedFuture(result));
        io.ddd4j.core.cqrs.eventstore.jackson.EventPayloadSerializer serializer =
                org.mockito.Mockito.mock(io.ddd4j.core.cqrs.eventstore.jackson.EventPayloadSerializer.class);
        org.mockito.Mockito.doReturn(new TestEvent(new TestId("order-1")))
                .when(serializer).deserialize(anyString(), any());

        io.ddd4j.core.cqrs.eventstore.StoredEvent event = new EsdbEventStore(client, "", serializer)
                .readAll(0, 1).get(0);
        assertThat(event.aggregateId().asString()).isEqualTo("order-1");
        assertThat(event.aggregateId().asTypedString()).isEqualTo("String:order-1");
        assertThat(event.aggregateId().toString()).isEqualTo("StringAggregateRootId[value=order-1]");
    }

    /**
     * 测试用聚合根标识，封装订单标识字符串并关联固定实体类型。
     */
    final static class TestId implements AggregateRootId {

        private static final long serialVersionUID = 0L;

        private final String value;

        private static final EntityType TYPE = new StringEntityType("Order");

        /**
         * 获取值。
         * @return 方法执行结果。
         */
        @Override
        public EntityType getType() {
            return TYPE;
        }

        /**
         * asString() 方法。
         * @return 结果字符串。
         */
        @Override
        public String asString() {
            return value;
        }

        /**
         * asTypedString() 方法。
         * @return 结果字符串。
         */
        @Override
        public String asTypedString() {
            return TYPE.asString() + ":" + value;
        }

        /**
         * 创建聚合根标识实例。
         *
         * @param value 聚合根标识的字符串值
         */
        @JsonCreator()
        TestId(@JsonProperty("value") String value) {
            this.value = value;
        }

        @JsonProperty("value")
        public String value() {
            return value;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (Objects.isNull(obj) || getClass() != obj.getClass()) {
                return false;
            }
            TestId other = (TestId) obj;
            return Objects.equals(this.value, other.value);
        }

        @Override
        public int hashCode() {
            int result = 0;
            result = 31 * result + Objects.hashCode(value);
            return result;
        }

        @Override
        public String toString() {
            return "TestId[value=" + value + "]";
        }
    }

    /**
     * 类型说明。
     * @author ddd4j
     * @since 3.0.x
     */
    static final class TestEvent extends DomainEvent<TestId> {
        TestEvent() {
            super();
        }

        TestEvent(TestId id) {
            super(new EntityIdPath(id));
        }
    }
}
