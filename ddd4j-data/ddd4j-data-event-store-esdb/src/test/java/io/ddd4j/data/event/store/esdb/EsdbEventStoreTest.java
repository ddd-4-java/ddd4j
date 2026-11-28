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
    /**
     * 测试事件流中使用的聚合类型名称。
     */
    private static final String ORDER_TYPE = "Order";
    /**
     * 用于隔离 <code>EventStoreDBClient</code> 调用并记录交互的测试替身。
     */
    @Mock
    EventStoreDBClient client;
    /**
     * 捕获被测代码传入的 <code>String</code> 参数以便断言。
     */
    @Captor
    ArgumentCaptor<String> streamCaptor;

    /**
     * 验证<code>EsdbEventStore.toExpectedRevision(0)</code> 等于 <code>ExpectedRevision.noStream()</code>，<code>EsdbEventStore.toExpectedRevision(3)</code> 等于 <code>ExpectedRevision.expectedRevision(2)</code>。
     */
    @Test
    void expectedRevisionShouldFollowCoreCurrentVersionContract() {
        assertThat(EsdbEventStore.toExpectedRevision(0)).isEqualTo(ExpectedRevision.noStream());
        assertThat(EsdbEventStore.toExpectedRevision(3)).isEqualTo(ExpectedRevision.expectedRevision(2));
    }

    /**
     * 验证调用了 <code>client.appendToStream(streamCaptor.capture(), any(AppendToStreamOptions.class), any(EventData[].class))</code>。
     */
    @Test
    void appendShouldUseAggregateTypeInStreamAndPreserveCoreEventIdentity() {
        TestId id = new TestId("order-1");
        when(client.appendToStream(anyString(), any(AppendToStreamOptions.class), any(EventData[].class)))
                .thenReturn(CompletableFuture.completedFuture(null));

        new EsdbEventStore(client, "test-").append(ORDER_TYPE, id, List.of(new TestEvent(id)), 0);

        verify(client).appendToStream(streamCaptor.capture(), any(AppendToStreamOptions.class), any(EventData[].class));
        assertThat(streamCaptor.getValue()).isEqualTo("test-Order::order-1");
    }

    /**
     * 验证未调用 <code>client</code>。
     */
    @Test
    void appendEmptyEventsMustNotCallClient() {
        new EsdbEventStore(client).append(ORDER_TYPE, new TestId("empty"), List.of(), 0);
        verifyNoInteractions(client);
    }

    /**
     * 验证被测调用 抛出 <code>AggregateVersionConflictException.class</code>。
     */
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

    /**
     * 验证被测调用 抛出 <code>IllegalStateException.class</code>。
     */
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

    /**
     * 验证被测调用 抛出 <code>IllegalStateException.class</code>。
     */
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

    /**
     * 在测试框架中执行 <code>org.mockito.Mockito.mock()</code>、<code>client.readStream()</code>、<code>anyString()</code>，验证该场景。
     */
    @Test
    void absentStronglyTypedStreamMustReadEmpty() {
        StreamNotFoundException absent = org.mockito.Mockito.mock(StreamNotFoundException.class);
        when(client.readStream(anyString(), any())).thenReturn(CompletableFuture.failedFuture(absent));
        assertThat(new EsdbEventStore(client).read(ORDER_TYPE, new TestId("missing"))).isEmpty();
    }

    /**
     * 验证<code>store.read(ORDER_TYPE, id).add(null)</code> 抛出 <code>UnsupportedOperationException.class</code>，<code>store.read(ORDER_TYPE, id, 1, 2).add(null)</code> 抛出 <code>UnsupportedOperationException.class</code>。
     */
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
     * 验证<code>event.aggregateId().asString()</code> 等于 <code>"order-1"</code>，<code>event.aggregateId().asTypedString()</code> 等于 <code>"String:order-1"</code>，<code>JsonMapper.builder().build().writeValueAsString(event.aggregateId())</code> 等于 <code>"\"order-1\""</code>。
     *
     * @throws Exception 处理测试数据的序列化时失败
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

    /**
     * 为测试聚合提供独立标识的值对象。
     */
    final static class TestId implements AggregateRootId {

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
         * 以给定状态构造 <code>TestId</code> 测试对象。
         *
         * @param value 用于组成测试标识的原始值
         */
        @JsonCreator()
        TestId(@JsonProperty("value") String value) {
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
            TestId other = (TestId) obj;
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
            return "TestId[value=" + value + "]";
        }
    }

    /**
     * 测试专用领域事件，用于控制事件类型和负载。
     */
    static final class TestEvent extends DomainEvent<TestId> {
        /**
         * 使用 <code>super()</code> 初始化 <code>TestEvent</code> 测试夹具。
         */
        TestEvent() {
            super();
        }

        /**
         * 使用 <code>super()</code> 初始化 <code>TestEvent</code> 测试夹具。
         *
         * @param id 当前要查找的测试记录标识
         */
        TestEvent(TestId id) {
            super(new EntityIdPath(id));
        }
    }
}
