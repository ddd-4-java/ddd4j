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
import io.ddd4j.core.cqrs.eventstore.EventStore;
import io.ddd4j.core.cqrs.eventstore.StoredEvent;
import io.ddd4j.kit.lang.JsonKit;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletionException;

/**
 * 基于 EventStoreDB 的 {@link EventStore} 实现（CQRS 写侧持久化）。
 *
 * <p>通过 {@link EventStoreDBClient}（gRPC 协议）对接 EventStoreDB 数据库，
 * 将 core EventStore SPI 语义映射到 EventStoreDB 流模型。
 *
 * <h3>流映射</h3>
 * <ul>
 *   <li><b>stream 名</b>：直接使用 {@code aggregateId}（可选 {@code streamPrefix} 前缀）</li>
 *   <li><b>版本</b>：SPI 的 {@code expectedVersion} 表示当前流中已有的事件数
 *       （新流为 0），映射到 ESDB 的 {@link ExpectedRevision}：
 *       <ul>
 *         <li>{@code expectedVersion == 0} → {@link ExpectedRevision#noStream()}</li>
 *         <li>{@code expectedVersion > 0} → {@link ExpectedRevision#expectedRevision(expectedVersion - 1)}
 *             （ESDB event number 0-based，= SPI version - 1）</li>
 *       </ul>
 *   </li>
 *   <li><b>position</b>：ESDB 全局 {@code commitPosition}（long）作为 SPI 的 position</li>
 *   <li><b>eventType</b>：事件类全限定名</li>
 *   <li><b>payload</b>：{@link JsonKit#toJson(Object)} 序列化的 JSON 文本</li>
 *   <li><b>timestamp</b>：{@link RecordedEvent#getCreated()}</li>
 * </ul>
 *
 * <h3>乐观并发控制</h3>
 * <p>{@link #append} 通过 ESDB 的 {@link ExpectedRevision} 实现乐观锁。
 * ESDB 版本冲突时抛出 {@link WrongExpectedVersionException}，本实现将其翻译为
 * {@link IllegalStateException}（与 {@link io.ddd4j.core.cqrs.eventstore.InMemoryEventStore}
 * 语义一致）。
 *
 * <h3>payload 反序列化</h3>
 * <p>读取时按 {@code eventType} 通过 {@code Class.forName} 还原事件类，
 * 再由 {@link JsonKit#toObject} 反序列化。若事件类已被删除或重命名，
 * 回退为 {@link JsonKit#toMap}（丢失类型信息），与 JPA/R2DBC 侧策略一致。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 3.0.0
 */
public class EsdbEventStore implements EventStore {

    private static final long DEFAULT_READ_LIMIT = 4096L;

    private final EventStoreDBClient client;
    private final String streamPrefix;

    /**
     * 创建 EventStoreDB 事件存储（无流前缀）。
     *
     * @param client EventStoreDB gRPC 客户端（由调用方管理生命周期）
     * @throws NullPointerException client 为 null 时抛出
     */
    public EsdbEventStore(EventStoreDBClient client) {
        this(client, "");
    }

    /**
     * 创建 EventStoreDB 事件存储（带流前缀）。
     *
     * <p>流前缀会拼接到 {@code aggregateId} 前面，用于命名空间隔离。
     * 例如前缀 {@code "order-"} + aggregateId {@code "001"} → 流名 {@code "order-001"}。
     *
     * @param client       EventStoreDB gRPC 客户端（由调用方管理生命周期）
     * @param streamPrefix 流名前缀（空字符串表示无前缀）
     * @throws NullPointerException 任一参数为 null 时抛出
     */
    public EsdbEventStore(EventStoreDBClient client, String streamPrefix) {
        this.client = Objects.requireNonNull(client, "client must not be null");
        this.streamPrefix = Objects.requireNonNull(streamPrefix, "streamPrefix must not be null");
    }

    /**
     * {@inheritDoc}
     *
     * <p>乐观锁：通过 ESDB 的 {@link ExpectedRevision} 机制实现。
     * ESDB 版本冲突（{@link WrongExpectedVersionException}）翻译为
     * {@link IllegalStateException}，消息格式与 {@link io.ddd4j.core.cqrs.eventstore.InMemoryEventStore}
     * 一致。
     */
    @Override
    public void append(String aggregateId, List<Object> events, long expectedVersion) {
        Objects.requireNonNull(aggregateId, "aggregateId must not be null");
        Objects.requireNonNull(events, "events must not be null");
        if (events.isEmpty()) {
            return;
        }

        String streamName = streamPrefix + aggregateId;
        ExpectedRevision expectedRevision = toExpectedRevision(expectedVersion);

        EventData[] eventDataArray = events.stream()
                .map(this::toEventData)
                .toArray(EventData[]::new);

        AppendToStreamOptions options = AppendToStreamOptions.get()
                .expectedRevision(expectedRevision);

        try {
            client.appendToStream(streamName, options, eventDataArray).join();
        } catch (CompletionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof WrongExpectedVersionException ex) {
                long actual = toEventCount(ex.getActualVersion());
                throw new IllegalStateException(
                        "Version conflict: expected " + expectedVersion + " but was " + actual);
            }
            throw new IllegalStateException("Failed to append events to stream: " + streamName, cause);
        }
    }

    /**
     * {@inheritDoc}
     *
     * <p>按 event number 升序读取指定聚合的全部事件。
     * 事件载荷通过 {@link JsonKit} 反序列化，类型无法还原时回退为 {@code Map}。
     */
    @Override
    public List<StoredEvent> read(String aggregateId) {
        Objects.requireNonNull(aggregateId, "aggregateId must not be null");

        String streamName = streamPrefix + aggregateId;
        ReadStreamOptions options = ReadStreamOptions.get()
                .forwards()
                .fromStart()
                .maxCount(DEFAULT_READ_LIMIT);

        try {
            ReadResult result = client.readStream(streamName, options).join();
            return result.getEvents().stream()
                    .map(this::toStoredEvent)
                    .toList();
        } catch (CompletionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof StreamNotFoundException) {
                return List.of();
            }
            throw new IllegalStateException("Failed to read events from stream: " + streamName, cause);
        }
    }

    /**
     * {@inheritDoc}
     *
     * <p>通过 ESDB 的 {@code readAll} 读取全局日志，
     * 按 {@code commitPosition >= fromPosition} 过滤后返回。
     * ESDB 全局日志包含系统事件（如 {@code $stream-metadata}），
     * 本方法仅返回用户事件。
     * 当构造时传入非空 {@code streamPrefix}，仅返回该前缀下的事件流；
     * 前缀为空时返回所有用户事件。
     */
    @Override
    public List<StoredEvent> readAll(long fromPosition, int limit) {
        Position esdbPosition = new Position(fromPosition, fromPosition);
        ReadAllOptions options = ReadAllOptions.get()
                .forwards()
                .fromPosition(esdbPosition)
                .maxCount(Math.max(limit * 2L, DEFAULT_READ_LIMIT));

        try {
            ReadResult result = client.readAll(options).join();
            List<StoredEvent> filtered = new ArrayList<>();
            for (ResolvedEvent resolved : result.getEvents()) {
                RecordedEvent recorded = resolved.getEvent();
                if (recorded == null) {
                    continue;
                }
                // 过滤系统流（以 $ 开头）
                if (recorded.getStreamId().startsWith("$")) {
                    continue;
                }
                // 过滤非本前缀的用户流（当 streamPrefix 非空时）
                if (!streamPrefix.isEmpty() && !recorded.getStreamId().startsWith(streamPrefix)) {
                    continue;
                }
                // 按 commitPosition 过滤
                long commitPosition = recorded.getPosition().getCommitUnsigned();
                if (commitPosition < fromPosition) {
                    continue;
                }
                StoredEvent storedEvent = toStoredEvent(resolved);
                filtered.add(storedEvent);
                if (filtered.size() >= limit) {
                    break;
                }
            }
            return filtered;
        } catch (CompletionException e) {
            throw new IllegalStateException("Failed to read events from global log", e.getCause());
        }
    }

    /**
     * 将 SPI expectedVersion 映射到 ESDB ExpectedRevision。
     *
     * <ul>
     *   <li>{@code expectedVersion == 0} → {@link ExpectedRevision#noStream()}（新流）</li>
     *   <li>{@code expectedVersion > 0} → {@link ExpectedRevision#expectedRevision(expectedVersion - 1)}
     *       （ESDB event number = SPI version - 1）</li>
     * </ul>
     *
     * @param expectedVersion SPI 期望版本（当前流事件数）
     * @return ESDB 期望修订号
     */
    static ExpectedRevision toExpectedRevision(long expectedVersion) {
        if (expectedVersion == 0) {
            return ExpectedRevision.noStream();
        }
        return ExpectedRevision.expectedRevision(expectedVersion - 1);
    }

    /**
     * 将 ESDB ExpectedRevision 转换为 SPI 事件计数。
     *
     * <p>ESDB 的 {@code noStream} 对应修订号 -1（事件数 0），
     * 其他修订号 = 事件数 - 1。
     *
     * @param revision ESDB 期望修订号
     * @return SPI 事件计数
     */
    private static long toEventCount(ExpectedRevision revision) {
        long raw = revision.toRawLong();
        if (raw == -1L) {
            return 0L;
        }
        return raw + 1L;
    }

    /**
     * 将领域事件转换为 ESDB EventData。
     *
     * <p>eventType 为事件类全限定名，payload 为 {@link JsonKit#toJson(Object)} 序列化的 JSON。
     *
     * @param event 领域事件对象
     * @return ESDB 事件数据
     */
    private EventData toEventData(Object event) {
        String eventType = event.getClass().getName();
        String json = JsonKit.toJson(event);
        return EventDataBuilder.json(eventType, json.getBytes()).build();
    }

    /**
     * 将 ESDB ResolvedEvent 转换为 core StoredEvent。
     *
     * <p>事件类型经 {@code Class.forName} 还原，payload 经 {@link JsonKit} 反序列化。
     * 若类不存在（被删除/重命名），回退为 {@link JsonKit#toMap}。
     *
     * @param resolved ESDB 解析事件
     * @return core 存储事件
     */
    private StoredEvent toStoredEvent(ResolvedEvent resolved) {
        RecordedEvent recorded = resolved.getEvent();
        String eventType = recorded.getEventType();
        Object event = deserializePayload(recorded, eventType);
        long commitPosition = recorded.getPosition().getCommitUnsigned();
        long revision = recorded.getRevision();
        Instant timestamp = recorded.getCreated();
        return new StoredEvent(recorded.getStreamId(), revision, event, commitPosition, timestamp);
    }

    /**
     * 反序列化事件载荷。
     *
     * <p>优先尝试按 {@code eventType} 还原为强类型对象；
     * 若类不存在，回退为 {@code Map}（丢失类型信息）。
     *
     * @param recorded  ESDB 已记录事件
     * @param eventType 事件类型全限定名
     * @return 反序列化后的事件对象或 Map
     */
    private Object deserializePayload(RecordedEvent recorded, String eventType) {
        String json = new String(recorded.getEventData());
        return io.ddd4j.core.cqrs.eventstore.EventDeserializer.deserialize(json, eventType);
    }
}
