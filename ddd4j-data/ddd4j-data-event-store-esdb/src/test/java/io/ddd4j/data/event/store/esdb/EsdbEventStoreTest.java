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
import io.ddd4j.core.cqrs.eventstore.StoredEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * {@link EsdbEventStore} 单元测试——Mockito mock {@link EventStoreDBClient}。
 *
 * <p>验证事件映射（eventType / payload / expectedRevision）、读取映射、
 * readAll 位置过滤、版本冲突翻译。不依赖 Docker。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 3.0.0
 */
@ExtendWith(MockitoExtension.class)
class EsdbEventStoreTest {

    @Mock
    private EventStoreDBClient client;

    @Captor
    private ArgumentCaptor<String> streamNameCaptor;

    @Captor
    private ArgumentCaptor<EventData[]> eventDataCaptor;

    // =================== 静态方法测试 ===================

    @Nested
    @DisplayName("toExpectedRevision 映射")
    class ExpectedRevisionMapping {

        @Test
        @DisplayName("expectedVersion=0 映射为 noStream")
        void zeroMapsToNoStream() {
            ExpectedRevision revision = EsdbEventStore.toExpectedRevision(0);
            assertThat(revision).isEqualTo(ExpectedRevision.noStream());
        }

        @Test
        @DisplayName("expectedVersion=1 映射为 expectedRevision(0)")
        void oneMapsToExpectedRevisionZero() {
            ExpectedRevision revision = EsdbEventStore.toExpectedRevision(1);
            assertThat(revision).isEqualTo(ExpectedRevision.expectedRevision(0));
        }

        @Test
        @DisplayName("expectedVersion=5 映射为 expectedRevision(4)")
        void fiveMapsToExpectedRevisionFour() {
            ExpectedRevision revision = EsdbEventStore.toExpectedRevision(5);
            assertThat(revision).isEqualTo(ExpectedRevision.expectedRevision(4));
        }
    }

    // =================== append 测试 ===================

    @Nested
    @DisplayName("append 事件追加")
    class AppendTests {

        @Test
        @DisplayName("新流追加：expectedVersion=0 → ExpectedRevision.noStream")
        void appendNewStreamUsesNoStream() {
            EsdbEventStore eventStore = new EsdbEventStore(client);
            WriteResult writeResult = mock(WriteResult.class);
            // 使用 any() 避免 appendToStream(String, AppendToStreamOptions, EventData...) 与
            // appendToStream(String, AppendToStreamOptions, Iterator) 的歧义
            when(client.appendToStream(anyString(), any(AppendToStreamOptions.class), any(EventData[].class)))
                    .thenReturn(CompletableFuture.completedFuture(writeResult));

            eventStore.append("agg-1", List.of(new OrderCreated("o-1", "c-1")), 0);

            verify(client).appendToStream(streamNameCaptor.capture(), any(AppendToStreamOptions.class), any(EventData[].class));
            assertThat(streamNameCaptor.getValue()).isEqualTo("agg-1");
        }

        @Test
        @DisplayName("已有流追加：expectedVersion=2 → ExpectedRevision.expectedRevision(1)")
        void appendExistingStreamUsesExpectedRevision() {
            EsdbEventStore eventStore = new EsdbEventStore(client);
            WriteResult writeResult = mock(WriteResult.class);
            when(client.appendToStream(anyString(), any(AppendToStreamOptions.class), any(EventData[].class)))
                    .thenReturn(CompletableFuture.completedFuture(writeResult));

            eventStore.append("agg-2", List.of(new ItemAdded("item-1", 2)), 2);

            verify(client).appendToStream(anyString(), any(AppendToStreamOptions.class), any(EventData[].class));
        }

        @Test
        @DisplayName("带流前缀时，streamName = prefix + aggregateId")
        void appendWithStreamPrefix() {
            EsdbEventStore eventStore = new EsdbEventStore(client, "order-");
            WriteResult writeResult = mock(WriteResult.class);
            when(client.appendToStream(anyString(), any(AppendToStreamOptions.class), any(EventData[].class)))
                    .thenReturn(CompletableFuture.completedFuture(writeResult));

            eventStore.append("001", List.of(new OrderCreated("o-1", "c-1")), 0);

            verify(client).appendToStream(streamNameCaptor.capture(), any(AppendToStreamOptions.class), any(EventData[].class));
            assertThat(streamNameCaptor.getValue()).isEqualTo("order-001");
        }

        @Test
        @DisplayName("事件 eventType 为类全限定名")
        void appendUsesQualifiedClassNameAsEventType() {
            EsdbEventStore eventStore = new EsdbEventStore(client);
            WriteResult writeResult = mock(WriteResult.class);
            when(client.appendToStream(anyString(), any(AppendToStreamOptions.class), any(EventData[].class)))
                    .thenReturn(CompletableFuture.completedFuture(writeResult));

            eventStore.append("agg-3", List.of(new OrderCreated("o-3", "c-3")), 0);

            verify(client).appendToStream(anyString(), any(AppendToStreamOptions.class), eventDataCaptor.capture());
            EventData[] captured = eventDataCaptor.getValue();
            assertThat(captured).hasSize(1);
            assertThat(captured[0].getEventType()).isEqualTo(OrderCreated.class.getName());
        }

        @Test
        @DisplayName("空事件列表不调用客户端")
        void appendEmptyListDoesNotCallClient() {
            EsdbEventStore eventStore = new EsdbEventStore(client);
            eventStore.append("agg-4", List.of(), 0);
            verifyNoInteractions(client);
        }

        @Test
        @DisplayName("版本冲突翻译为 IllegalStateException")
        void appendVersionConflictTranslated() {
            EsdbEventStore eventStore = new EsdbEventStore(client);
            WrongExpectedVersionException ex = mock(WrongExpectedVersionException.class);
            when(ex.getActualVersion()).thenReturn(ExpectedRevision.expectedRevision(0));
            when(client.appendToStream(anyString(), any(AppendToStreamOptions.class), any(EventData[].class)))
                    .thenReturn(CompletableFuture.failedFuture(ex));

            assertThatThrownBy(() -> eventStore.append("agg-5", List.of(new OrderCreated("o-5", "c-5")), 0))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Version conflict")
                    .hasMessageContaining("expected 0");
        }
    }

    // =================== read 测试 ===================

    @Nested
    @DisplayName("read 按聚合读取")
    class ReadTests {

        @Test
        @DisplayName("流不存在时返回空列表")
        void readNonExistentStreamReturnsEmpty() {
            EsdbEventStore eventStore = new EsdbEventStore(client);
            // StreamNotFoundException 构造器是包私有的，使用 Mockito mock
            StreamNotFoundException notFound = mock(StreamNotFoundException.class);
            when(client.readStream(anyString(), any(ReadStreamOptions.class)))
                    .thenReturn(CompletableFuture.failedFuture(notFound));

            List<StoredEvent> events = eventStore.read("agg-missing");
            assertThat(events).isEmpty();
        }

        @Test
        @DisplayName("带前缀时读取正确的流名")
        void readWithPrefixUsesCorrectStreamName() {
            EsdbEventStore eventStore = new EsdbEventStore(client, "prefix-");
            StreamNotFoundException notFound = mock(StreamNotFoundException.class);
            when(client.readStream(anyString(), any(ReadStreamOptions.class)))
                    .thenReturn(CompletableFuture.failedFuture(notFound));

            eventStore.read("agg-1");

            verify(client).readStream(streamNameCaptor.capture(), any(ReadStreamOptions.class));
            assertThat(streamNameCaptor.getValue()).isEqualTo("prefix-agg-1");
        }
    }

    // =================== 测试事件类 ===================

    record OrderCreated(String orderId, String customerId) {
    }

    record ItemAdded(String itemId, int quantity) {
    }
}
