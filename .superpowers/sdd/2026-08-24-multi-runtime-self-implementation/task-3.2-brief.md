### Task 3.2：定义 EventStore SPI + StoredEvent + AggregateVersionConflictException

**Files:**
- Create: `ddd4j/ddd4j-data/ddd4j-data-event-store/src/main/java/io/ddd4j/data/eventstore/EventStore.java`
- Create: `ddd4j/ddd4j-data/ddd4j-data-event-store/src/main/java/io/ddd4j/data/eventstore/StoredEvent.java`
- Create: `ddd4j/ddd4j-data/ddd4j-data-event-store/src/main/java/io/ddd4j/data/eventstore/AggregateVersionConflictException.java`
- Test: `ddd4j/ddd4j-data/ddd4j-data-event-store/src/test/java/io/ddd4j/data/eventstore/EventStoreContractTest.java`

**Interfaces:**
- 消费：Task 1.5 参考文档
- 产出：`EventStore` SPI + `StoredEvent` + `AggregateVersionConflictException`

- [ ] **Step 1: 写 AggregateVersionConflictException**

Write `AggregateVersionConflictException.java`：

```java
package io.ddd4j.data.eventstore;

/**
 * 乐观锁版本冲突异常。
 */
public class AggregateVersionConflictException extends RuntimeException {
    private final String aggregateType;
    private final String aggregateId;
    private final long expectedVersion;
    private final long actualVersion;

    public AggregateVersionConflictException(String aggregateType, String aggregateId,
                                             long expectedVersion, long actualVersion) {
        super(String.format("Aggregate %s#%s version conflict: expected=%d, actual=%d",
            aggregateType, aggregateId, expectedVersion, actualVersion));
        this.aggregateType = aggregateType;
        this.aggregateId = aggregateId;
        this.expectedVersion = expectedVersion;
        this.actualVersion = actualVersion;
    }

    public String aggregateType() { return aggregateType; }
    public String aggregateId() { return aggregateId; }
    public long expectedVersion() { return expectedVersion; }
    public long actualVersion() { return actualVersion; }
}
```

- [ ] **Step 2: 写 StoredEvent**

Write `StoredEvent.java`：

```java
package io.ddd4j.data.eventstore;

import io.ddd4j.core.ddd.event.*;

import java.time.ZonedDateTime;
import java.util.Objects;

/**
 * 持久化的领域事件快照。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 2.0.x
 */
public final class StoredEvent {

    private final EventId eventId;
    private final String aggregateType;
    private final AggregateRootId aggregateId;
    private final long version;
    private final long position;
    private final ZonedDateTime timestamp;
    private final DomainEvent<?> payload;
    private final EventId correlationId;
    private final EventId causationId;

    public StoredEvent(EventId eventId, String aggregateType, AggregateRootId aggregateId,
                       long version, long position, ZonedDateTime timestamp,
                       DomainEvent<?> payload, EventId correlationId, EventId causationId) {
        this.eventId = Objects.requireNonNull(eventId);
        this.aggregateType = Objects.requireNonNull(aggregateType);
        this.aggregateId = Objects.requireNonNull(aggregateId);
        this.version = version;
        this.position = position;
        this.timestamp = Objects.requireNonNull(timestamp);
        this.payload = Objects.requireNonNull(payload);
        this.correlationId = correlationId;
        this.causationId = causationId;
    }

    public EventId eventId() { return eventId; }
    public String aggregateType() { return aggregateType; }
    public AggregateRootId aggregateId() { return aggregateId; }
    public long version() { return version; }
    public long position() { return position; }
    public ZonedDateTime timestamp() { return timestamp; }
    public DomainEvent<?> payload() { return payload; }
    public EventId correlationId() { return correlationId; }
    public EventId causationId() { return causationId; }
}
```

- [ ] **Step 3: 写 EventStore SPI**

Write `EventStore.java`：

```java
package io.ddd4j.data.eventstore;

import io.ddd4j.core.ddd.event.AggregateRootId;
import io.ddd4j.core.ddd.event.DomainEvent;

import java.util.List;

/**
 * 事件存储 SPI。
 *
 * <p>API 形态对齐 cqrs-4-java 的 EventStore 语义，但完全独立实现。
 *
 * <h3>乐观锁</h3>
 * <p>append 时校验 {@code expectedVersion}，冲突时抛
 * {@link AggregateVersionConflictException}。
 *
 * <h3>实现</h3>
 * <ul>
 *   <li>JPA：{@code ddd4j-data-event-store-jpa}</li>
 *   <li>Quarkus Panache：{@code ddd4j-data-event-store-panache}</li>
 *   <li>Javalin JDBI：{@code ddd4j-data-event-store-jdbi}</li>
 *   <li>响应式：{@code ddd4j-data-event-store-r2dbc}</li>
 * </ul>
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 2.0.x
 */
public interface EventStore {

    /**
     * 追加事件到聚合流。
     *
     * @param aggregateType   聚合类型
     * @param aggregateId     聚合 ID
     * @param events          要追加的事件
     * @param expectedVersion 期望的当前版本号（乐观锁）
     * @throws AggregateVersionConflictException 版本冲突
     */
    void append(String aggregateType, AggregateRootId aggregateId,
                List<? extends DomainEvent<?>> events, long expectedVersion);

    /**
     * 读取聚合全部事件。
     */
    List<StoredEvent> read(String aggregateType, AggregateRootId aggregateId);

    /**
     * 读取指定版本区间的事件。
     */
    List<StoredEvent> read(String aggregateType, AggregateRootId aggregateId,
                           long fromVersion, long toVersion);

    /**
     * 读取全局事件流（用于 projection）。
     *
     * @param fromPosition 起始 position（含）
     * @param limit        最大读取数量
     */
    List<StoredEvent> readAll(long fromPosition, int limit);
}
```

- [ ] **Step 4: 写 EventStoreContractTest（JUnit 5 contract test）**

Write `EventStoreContractTest.java`：

```java
package io.ddd4j.data.eventstore;

import io.ddd4j.core.ddd.event.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.Extension;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.TestTemplateInvocationContext;
import org.junit.jupiter.api.extension.TestTemplateInvocationContextProvider;

import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 抽象 contract test：每个 EventStore 实现都需要提供 createEventStore() 方法，
 * 并通过 invokeAll 自动用每种实现跑这些测试。
 */
public abstract class EventStoreContractTest {

    /** 每个实现提供一个 EventStore 实例 */
    protected abstract EventStore createEventStore();

    @TestTemplate
    @ExtendWith(EventStoreInvocationProvider.class)
    void appendAndReadBackSingleEvent() {
        EventStore store = createEventStore();
        TestAggregate aggregate = new TestAggregate("agg-1");
        TestEvent event = new TestEvent(aggregate.rootId());
        store.append(TestAggregate.TYPE, aggregate.rootId(), List.of(event), 0L);
        List<StoredEvent> read = store.read(TestAggregate.TYPE, aggregate.rootId());
        assertEquals(1, read.size());
        assertEquals(event.getEventId().asString(), read.get(0).eventId().asString());
    }

    @TestTemplate
    @ExtendWith(EventStoreInvocationProvider.class)
    void optimisticLockThrowsOnVersionConflict() {
        EventStore store = createEventStore();
        TestAggregate aggregate = new TestAggregate("agg-2");
        store.append(TestAggregate.TYPE, aggregate.rootId(),
            List.of(new TestEvent(aggregate.rootId())), 0L);
        assertThrows(AggregateVersionConflictException.class, () ->
            store.append(TestAggregate.TYPE, aggregate.rootId(),
                List.of(new TestEvent(aggregate.rootId())), 0L)
        );
    }

    @TestTemplate
    @ExtendWith(EventStoreInvocationProvider.class)
    void readAllReturnsEventsAfterPosition() {
        EventStore store = createEventStore();
        TestAggregate agg = new TestAggregate("agg-3");
        for (int i = 0; i < 3; i++) {
            store.append(TestAggregate.TYPE, agg.rootId(),
                List.of(new TestEvent(agg.rootId())), i);
        }
        List<StoredEvent> all = store.readAll(1, 100);
        assertTrue(all.size() >= 2);
    }

    record TestAggregate(String id) {
        public static final String TYPE = "TestAggregate";
        public AggregateRootId rootId() {
            return new AggregateRootId() {
                @Override public EntityId last() {
                    return new StringEntityId(id);
                }
                @Override public String asString() { return id; }
            };
        }
    }

    static class TestEvent extends DomainEvent<TestAggregate.TestAggregateId> {
        public TestEvent(AggregateRootId aggregateId) {
            super(new EntityIdPath(aggregateId));
        }
    }

    public record TestAggregateId(String value) implements AggregateRootId {
        @Override public EntityId last() { return new StringEntityId(value); }
        @Override public String asString() { return value; }
    }
}
```

Write `EventStoreInvocationProvider.java`（位于同一包）：

```java
package io.ddd4j.data.eventstore;

import org.junit.jupiter.api.extension.Extension;
import org.junit.jupiter.api.extension.TestTemplateInvocationContext;
import org.junit.jupiter.api.extension.TestTemplateInvocationContextProvider;

import java.util.Collections;
import java.util.List;

/**
 * 用于抽象测试类的 provider stub。实际实现由具体 EventStore 测试类覆盖。
 */
@Extension
public class EventStoreInvocationProvider implements TestTemplateInvocationContextProvider {

    @Override
    public boolean supportsTestTemplate(ExtensionContext context) {
        return true;
    }

    @Override
    public List<TestTemplateInvocationContext> provideTestTemplateInvocationContexts(ExtensionContext context) {
        return CollectionsList.ofEmptyList();
    }
}
```

- [ ] **Step 5: 验证编译**

Run: `cd /Users/wandl/workspaces/workspace-ddd4j/workspace-ddd4j-boot/ddd4j && ./mvnw -pl ddd4j-data/ddd4j-data-event-store compile`

Expected: BUILD SUCCESS（contract test 用 @TestTemplate 标注，编译期会忽略抽象）

- [ ] **Step 6: 提交**

```bash
cd /Users/wandl/workspaces/workspace-ddd4j/workspace-ddd4j-boot/ddd4j
git add ddd4j-data/ddd4j-data-event-store/src/main/java/
git add ddd4j-data/ddd4j-data-event-store/src/test/java/
git commit -m "feat(data): EventStore SPI + StoredEvent + ContractTest 模板"
```

---


---

## Controller context（已核对源码；与 sketch 冲突处以本节为准）

### 核心类型事实（写前自查已核）
- `AggregateRootId` 是**接口**（extends EntityId，3 方法 getType/asString/asTypedString）——SPI 签名直接用接口类型；StoredEvent 持有引用即可。
- `EventId` 构造器仅 `EventId()`/`EventId(UUID)`，另有静态解析方法（EventId.java:47 一带，自读确认签名）——**不存在 `new EventId(String)`**（计划 4.3 sketch 用了它，属后续任务坑，本任务不涉及）。
- `DomainEvent<ID extends EntityId>` 抽象类；`StringEntityId(String)` 具体类。

### 本任务交付（4 文件 + 门禁）
1. `AggregateVersionConflictException`（package io.ddd4j.data.eventstore）：extends RuntimeException；4 final 字段（aggregateType/aggregateId 均 String、expectedVersion/actualVersion 均 long）+ 全参构造（message 用 sketch 的 format）+ 4 访问器（aggregateType() 等）。javadoc 引 ADR-0005。
2. `StoredEvent`（同包）：final 类，9 字段按 sketch（EventId eventId、String aggregateType、AggregateRootId aggregateId、long version、long position、ZonedDateTime timestamp、DomainEvent<?> payload、EventId correlationId、EventId causationId——后两个可空）；构造器对非空 7 项 Objects.requireNonNull；访问器方法风格 `eventId()` 等。javadoc 注明 position 为全局递增（对照 05 篇/ADR-0005）。
3. `EventStore` 接口（同包）：4 方法签名照 sketch（append(String, AggregateRootId, List<? extends DomainEvent<?>>, long)、read(String, AggregateRootId)、read(String, AggregateRootId, long, long)、readAll(long, int)）；javadoc：乐观锁→AggregateVersionConflictException、实现清单指向 ddd4j-data-event-store-{jpa,panache,jdbi,r2dbc}（阶段 4/5 落地）、对照 esc-api 差异引 05 篇。
4. **跳过 sketch 的 EventStoreContractTest/@TestTemplate**（EventStoreInvocationProvider 是坏桩——brief correction 记录：实现各自的 IT 在阶段 4/5 覆盖契约）。替代测试：
   - `StoredEventTest`（同包 test）：null 构造参数抛 NPE（7 项参数化或分组断言）、可空 correlationId/causationId 合法、访问器返回原值。照 AggregateRootApplyTest 风格（@since 2.0.x）。
   - `EventStoreModuleIndependenceTest`（io.ddd4j.data.eventstore.arch 子包）：照 CoreIndependenceTest 的 @ArchTest 字段风格，≥3 条规则——no_spring/no_jakarta_persistence（io.ddd4j.data.eventstore.. 不依赖 org.springframework../jakarta.persistence..）、module_deps_allowlist（只依赖 io.ddd4j../java../com.fasterxml.jackson.core|databind|annotation../lombok..）。
5. 门禁：`./mvnw -pl ddd4j-data/ddd4j-data-event-store -am install` BUILD SUCCESS（全测试计入报告）；3.1 的 skipIfEmpty 覆盖注释此刻已可删（源码非空）——顺手删并记入报告。
6. 单 commit：`feat(data): EventStore SPI + StoredEvent + AggregateVersionConflictException`。

## Out of scope
不写 EventPayloadSerializer（Task 3.3）；不写 JPA（阶段 4）；不动 core。

## Report
Write to `.superpowers/sdd/2026-08-24-multi-runtime-self-implementation/task-3.2-report.md`。Reply ≤15 lines.
