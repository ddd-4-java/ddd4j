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

import com.eventstore.dbclient.*;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.json.JsonMapper;
import io.ddd4j.core.cqrs.eventstore.AggregateVersionConflictException;
import io.ddd4j.core.ddd.event.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * ESDB 强类型 EventStore 的单元契约。
 */
@ExtendWith(MockitoExtension.class)
class EsdbEventStoreTest {
    /** \u6d4b\u8bd5\u4e8b\u4ef6\u6d41\u4e2d\u4f7f\u7528\u7684\u805a\u5408\u7c7b\u578b\u540d\u79f0\u3002 */
    private static final String ORDER_TYPE = "Order";
    /** \u7528\u4e8e\u9694\u79bb <code>EventStoreDBClient</code> \u8c03\u7528\u5e76\u8bb0\u5f55\u4ea4\u4e92\u7684\u6d4b\u8bd5\u66ff\u8eab\u3002 */
    @Mock
    EventStoreDBClient client;
    /** \u6355\u83b7\u88ab\u6d4b\u4ee3\u7801\u4f20\u5165\u7684 <code>String</code> \u53c2\u6570\u4ee5\u4fbf\u65ad\u8a00\u3002 */
    @Captor
    ArgumentCaptor<String> streamCaptor;

    /** \u9a8c\u8bc1<code>EsdbEventStore.toExpectedRevision(0)</code> \u7b49\u4e8e <code>ExpectedRevision.noStream()</code>\uff0c<code>EsdbEventStore.toExpectedRevision(3)</code> \u7b49\u4e8e <code>ExpectedRevision.expectedRevision(2)</code>\u3002 */
    @Test
    void expectedRevisionShouldFollowCoreCurrentVersionContract() {
        assertThat(EsdbEventStore.toExpectedRevision(0)).isEqualTo(ExpectedRevision.noStream());
        assertThat(EsdbEventStore.toExpectedRevision(3)).isEqualTo(ExpectedRevision.expectedRevision(2));
    }

    /** \u9a8c\u8bc1\u8c03\u7528\u4e86 <code>client.appendToStream(streamCaptor.capture(), any(AppendToStreamOptions.class), any(EventData[].class))</code>\u3002 */
    @Test
    void appendShouldUseAggregateTypeInStreamAndPreserveCoreEventIdentity() {
        TestId id = new TestId("order-1");
        when(client.appendToStream(anyString(), any(AppendToStreamOptions.class), any(EventData[].class)))
                .thenReturn(CompletableFuture.completedFuture(null));

        new EsdbEventStore(client, "test-").append(ORDER_TYPE, id, List.of(new TestEvent(id)), 0);

        verify(client).appendToStream(streamCaptor.capture(), any(AppendToStreamOptions.class), any(EventData[].class));
        assertThat(streamCaptor.getValue()).isEqualTo("test-Order::order-1");
    }

    /** \u9a8c\u8bc1\u672a\u8c03\u7528 <code>client</code>\u3002 */
    @Test
    void appendEmptyEventsMustNotCallClient() {
        new EsdbEventStore(client).append(ORDER_TYPE, new TestId("empty"), List.of(), 0);
        verifyNoInteractions(client);
    }

    /** \u9a8c\u8bc1\u88ab\u6d4b\u8c03\u7528 \u629b\u51fa <code>AggregateVersionConflictException.class</code>\u3002 */
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

    /** \u9a8c\u8bc1\u88ab\u6d4b\u8c03\u7528 \u629b\u51fa <code>IllegalStateException.class</code>\u3002 */
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

    /** \u9a8c\u8bc1\u88ab\u6d4b\u8c03\u7528 \u629b\u51fa <code>IllegalStateException.class</code>\u3002 */
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

    /** \u5728\u6d4b\u8bd5\u6846\u67b6\u4e2d\u6267\u884c <code>org.mockito.Mockito.mock()</code>\u3001<code>client.readStream()</code>\u3001<code>anyString()</code>\uff0c\u9a8c\u8bc1\u8be5\u573a\u666f\u3002 */
    @Test
    void absentStronglyTypedStreamMustReadEmpty() {
        StreamNotFoundException absent = org.mockito.Mockito.mock(StreamNotFoundException.class);
        when(client.readStream(anyString(), any())).thenReturn(CompletableFuture.failedFuture(absent));
        assertThat(new EsdbEventStore(client).read(ORDER_TYPE, new TestId("missing"))).isEmpty();
    }

    /** \u9a8c\u8bc1<code>store.read(ORDER_TYPE, id).add(null)</code> \u629b\u51fa <code>UnsupportedOperationException.class</code>\uff0c<code>store.read(ORDER_TYPE, id, 1, 2).add(null)</code> \u629b\u51fa <code>UnsupportedOperationException.class</code>\u3002 */
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

    /**
     * \u9a8c\u8bc1<code>event.aggregateId().asString()</code> \u7b49\u4e8e <code>"order-1"</code>\uff0c<code>event.aggregateId().asTypedString()</code> \u7b49\u4e8e <code>"String:order-1"</code>\uff0c<code>JsonMapper.builder().build().writeValueAsString(event.aggregateId())</code> \u7b49\u4e8e <code>"\"order-1\""</code>\u3002
     * @throws Exception \u5904\u7406\u6d4b\u8bd5\u6570\u636e\u7684\u5e8f\u5217\u5316\u65f6\u5931\u8d25
     */
    @Test
    void readAllShouldExposeStableAggregateIdString() throws Exception {
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
        assertThat(JsonMapper.builder().build().writeValueAsString(event.aggregateId()))
                .isEqualTo("\"order-1\"");
    }

    /** \u4e3a\u6d4b\u8bd5\u805a\u5408\u63d0\u4f9b\u72ec\u7acb\u6807\u8bc6\u7684\u503c\u5bf9\u8c61\u3002 */
    final static class TestId implements AggregateRootId {

        /** Java \u5e8f\u5217\u5316\u4f7f\u7528\u7684\u7248\u672c\u6807\u8bc6\u3002 */
        private static final long serialVersionUID = 0L;
        /** \u6d4b\u8bd5\u805a\u5408\u6ce8\u518c\u548c\u53cd\u5e8f\u5217\u5316\u6240\u7528\u7684\u5b9e\u4f53\u7c7b\u578b\u6807\u8bc6\u3002 */
        private static final EntityType TYPE = new StringEntityType("Order");
        /** \u7528\u4e8e\u7ec4\u6210\u6d4b\u8bd5\u6807\u8bc6\u7684\u539f\u59cb\u503c\u3002 */
        private final String value;

        /**
         * \u4ee5\u7ed9\u5b9a\u72b6\u6001\u6784\u9020 <code>TestId</code> \u6d4b\u8bd5\u5bf9\u8c61\u3002
         * @param value \u7528\u4e8e\u7ec4\u6210\u6d4b\u8bd5\u6807\u8bc6\u7684\u539f\u59cb\u503c
         */
        @JsonCreator()
        TestId(@JsonProperty("value") String value) {
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

        /** {@inheritDoc} */
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

        /** {@inheritDoc} */
        @Override
        public int hashCode() {
            int result = 0;
            result = 31 * result + Objects.hashCode(value);
            return result;
        }

        /** {@inheritDoc} */
        @Override
        public String toString() {
            return "TestId[value=" + value + "]";
        }
    }

    /** \u6d4b\u8bd5\u4e13\u7528\u9886\u57df\u4e8b\u4ef6\uff0c\u7528\u4e8e\u63a7\u5236\u4e8b\u4ef6\u7c7b\u578b\u548c\u8d1f\u8f7d\u3002 */
    static final class TestEvent extends DomainEvent<TestId> {
        /** \u4f7f\u7528 <code>super()</code> \u521d\u59cb\u5316 <code>TestEvent</code> \u6d4b\u8bd5\u5939\u5177\u3002 */
        TestEvent() {
            super();
        }

        /**
         * \u4f7f\u7528 <code>super()</code> \u521d\u59cb\u5316 <code>TestEvent</code> \u6d4b\u8bd5\u5939\u5177\u3002
         * @param id \u5f53\u524d\u8981\u67e5\u627e\u7684\u6d4b\u8bd5\u8bb0\u5f55\u6807\u8bc6
         */
        TestEvent(TestId id) {
            super(new EntityIdPath(id));
        }
    }
}
